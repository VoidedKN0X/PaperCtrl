package com.vknxworks.paperctrl.scan

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.PointF
import androidx.annotation.StringRes
import com.vknxworks.paperctrl.R
import kotlinx.serialization.Serializable
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/** How a scanned page looks in the PDF. */
@Serializable
enum class ScanFilter(@StringRes val label: Int) {
    ORIGINAL(R.string.filter_original),
    ENHANCED(R.string.filter_enhanced),
    GREY(R.string.filter_grey),
    BW(R.string.filter_bw),
}

/** A corner of the page, as a fraction (0..1) of the image's width and height. */
@Serializable
data class Corner(val x: Float, val y: Float)

/** Edge detection, perspective correction and filters, all on-device with OpenCV. */
object ImageOps {

    @Volatile private var loaded = false

    fun ensureLoaded(): Boolean {
        if (!loaded) loaded = runCatching { OpenCVLoader.initLocal() }.getOrDefault(false)
        return loaded
    }

    val FULL_PAGE = listOf(Corner(0f, 0f), Corner(1f, 0f), Corner(1f, 1f), Corner(0f, 1f))

    /**
     * Finds the largest four-sided shape in a greyscale image (a sheet of paper), as corners in
     * top-left, top-right, bottom-right, bottom-left order. Null when nothing page-like is found.
     */
    fun detect(gray: Mat): List<Corner>? {
        if (!ensureLoaded() || gray.empty()) return null
        val scale = 360.0 / max(gray.cols(), gray.rows())
        val small = Mat()
        Imgproc.resize(gray, small, Size(), scale, scale, Imgproc.INTER_AREA)
        val w = small.cols().toDouble()
        val h = small.rows().toDouble()
        val blurred = Mat()
        Imgproc.GaussianBlur(small, blurred, Size(5.0, 5.0), 0.0)
        val edges = Mat()
        Imgproc.Canny(blurred, edges, 40.0, 120.0)
        Imgproc.dilate(edges, edges, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0)))
        val contours = mutableListOf<MatOfPoint>()
        Imgproc.findContours(edges, contours, Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
        var best: List<Point>? = null
        var bestArea = w * h * 0.15 // a page fills at least ~15% of the frame
        for (c in contours) {
            val c2f = MatOfPoint2f(*c.toArray())
            val peri = Imgproc.arcLength(c2f, true)
            val approx = MatOfPoint2f()
            Imgproc.approxPolyDP(c2f, approx, 0.02 * peri, true)
            val pts = approx.toArray()
            if (pts.size == 4) {
                val area = abs(Imgproc.contourArea(approx))
                if (area > bestArea && Imgproc.isContourConvex(MatOfPoint(*pts))) {
                    bestArea = area
                    best = pts.toList()
                }
            }
            c2f.release(); approx.release(); c.release()
        }
        small.release(); blurred.release(); edges.release()
        val quad = best ?: return null
        return order(quad).map { Corner((it.x / w).toFloat().coerceIn(0f, 1f), (it.y / h).toFloat().coerceIn(0f, 1f)) }
    }

    fun detect(bitmap: Bitmap): List<Corner>? {
        if (!ensureLoaded()) return null
        val rgba = Mat()
        Utils.bitmapToMat(bitmap, rgba)
        val gray = Mat()
        Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
        rgba.release()
        return detect(gray).also { gray.release() }
    }

    /** Top-left, top-right, bottom-right, bottom-left. */
    private fun order(p: List<Point>): List<Point> {
        val tl = p.minBy { it.x + it.y }
        val br = p.maxBy { it.x + it.y }
        val tr = p.minBy { it.y - it.x }
        val bl = p.maxBy { it.y - it.x }
        return listOf(tl, tr, br, bl)
    }

    /**
     * Straightens the page inside [corners], applies [filter] and [rotation] (degrees clockwise), and
     * scales the result so its longest side is at most [maxSide] pixels.
     */
    fun render(src: Bitmap, corners: List<Corner>, filter: ScanFilter, rotation: Int, maxSide: Int): Bitmap {
        if (!ensureLoaded()) return src.copy(Bitmap.Config.ARGB_8888, false)
        val mat = Mat()
        Utils.bitmapToMat(src, mat)
        val sw = src.width.toDouble()
        val sh = src.height.toDouble()
        val p = corners.map { Point(it.x * sw, it.y * sh) }
        val widthPx = max(dist(p[0], p[1]), dist(p[3], p[2]))
        val heightPx = max(dist(p[0], p[3]), dist(p[1], p[2]))
        val scale = minOf(1.0, maxSide / max(widthPx, heightPx))
        val outW = (widthPx * scale).roundToInt().coerceAtLeast(16)
        val outH = (heightPx * scale).roundToInt().coerceAtLeast(16)
        val isFull = corners == FULL_PAGE
        var out = Mat()
        if (isFull) {
            Imgproc.resize(mat, out, Size(outW.toDouble(), outH.toDouble()), 0.0, 0.0, Imgproc.INTER_AREA)
        } else {
            val from = MatOfPoint2f(*p.toTypedArray())
            val to = MatOfPoint2f(
                Point(0.0, 0.0), Point(outW - 1.0, 0.0), Point(outW - 1.0, outH - 1.0), Point(0.0, outH - 1.0),
            )
            val m = Imgproc.getPerspectiveTransform(from, to)
            Imgproc.warpPerspective(mat, out, m, Size(outW.toDouble(), outH.toDouble()), Imgproc.INTER_LINEAR)
            from.release(); to.release(); m.release()
        }
        mat.release()
        out = applyFilter(out, filter)
        val rotateCode = when (((rotation % 360) + 360) % 360) {
            90 -> Core.ROTATE_90_CLOCKWISE
            180 -> Core.ROTATE_180
            270 -> Core.ROTATE_90_COUNTERCLOCKWISE
            else -> null
        }
        if (rotateCode != null) {
            val r = Mat()
            Core.rotate(out, r, rotateCode)
            out.release()
            out = r
        }
        val bmp = Bitmap.createBitmap(out.cols(), out.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(out, bmp)
        out.release()
        return bmp
    }

    /** Filters on an RGBA image; returns a new RGBA image and releases [rgba]. */
    private fun applyFilter(rgba: Mat, filter: ScanFilter): Mat {
        when (filter) {
            ScanFilter.ORIGINAL -> return rgba
            ScanFilter.ENHANCED -> {
                // Shadow removal: divide each channel by its blurred background, then stretch contrast.
                val rgb = Mat()
                Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB)
                rgba.release()
                val channels = mutableListOf<Mat>()
                Core.split(rgb, channels)
                val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(7.0, 7.0))
                val fixed = channels.map { ch ->
                    val bg = Mat()
                    Imgproc.dilate(ch, bg, kernel)
                    Imgproc.medianBlur(bg, bg, 21)
                    val diff = Mat()
                    Core.absdiff(ch, bg, diff)
                    Core.bitwise_not(diff, diff)
                    Core.normalize(diff, diff, 0.0, 255.0, Core.NORM_MINMAX, CvType.CV_8UC1)
                    bg.release(); ch.release()
                    diff
                }
                val merged = Mat()
                Core.merge(fixed, merged)
                fixed.forEach { it.release() }
                rgb.release(); kernel.release()
                val out = Mat()
                Imgproc.cvtColor(merged, out, Imgproc.COLOR_RGB2RGBA)
                merged.release()
                return out
            }
            ScanFilter.GREY -> {
                val gray = Mat()
                Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
                rgba.release()
                Core.normalize(gray, gray, 0.0, 255.0, Core.NORM_MINMAX)
                val out = Mat()
                Imgproc.cvtColor(gray, out, Imgproc.COLOR_GRAY2RGBA)
                gray.release()
                return out
            }
            ScanFilter.BW -> {
                val gray = Mat()
                Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
                rgba.release()
                Imgproc.GaussianBlur(gray, gray, Size(3.0, 3.0), 0.0)
                val bw = Mat()
                val block = (max(gray.cols(), gray.rows()) / 60).let { if (it % 2 == 0) it + 1 else it }.coerceIn(15, 61)
                Imgproc.adaptiveThreshold(gray, bw, 255.0, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY, block, 12.0)
                gray.release()
                val out = Mat()
                Imgproc.cvtColor(bw, out, Imgproc.COLOR_GRAY2RGBA)
                bw.release()
                return out
            }
        }
    }

    private fun dist(a: Point, b: Point) = hypot(a.x - b.x, a.y - b.y)

    /** Decodes [file] upright (EXIF applied), scaled so its longest side is at most [maxSide]. */
    fun decode(file: File, maxSide: Int): Bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val w = info.size.width
        val h = info.size.height
        val s = maxSide.toFloat() / max(w, h)
        if (s < 1f) decoder.setTargetSize((w * s).roundToInt().coerceAtLeast(1), (h * s).roundToInt().coerceAtLeast(1))
    }

    /** Image size without decoding it. */
    fun size(file: File): Pair<Int, Int> {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, o)
        return o.outWidth to o.outHeight
    }

    fun saveJpeg(bitmap: Bitmap, file: File, quality: Int = 90) {
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }
    }

    /** Converts normalised corners from a sensor-oriented frame to an upright one. */
    fun rotateCorners(corners: List<Corner>, rotationDegrees: Int): List<Corner> = corners.map { c ->
        when (((rotationDegrees % 360) + 360) % 360) {
            90 -> Corner(1f - c.y, c.x)
            180 -> Corner(1f - c.x, 1f - c.y)
            270 -> Corner(c.y, 1f - c.x)
            else -> c
        }
    }

    fun toPoints(c: List<Corner>, w: Float, h: Float) = c.map { PointF(it.x * w, it.y * h) }
}
