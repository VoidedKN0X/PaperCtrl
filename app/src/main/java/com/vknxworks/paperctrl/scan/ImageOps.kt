package com.vknxworks.paperctrl.scan

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.PointF
import androidx.annotation.StringRes
import com.vknxworks.paperctrl.R
import kotlinx.serialization.Serializable
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import com.vknxworks.paperctrl.scan.engine.Filters
import com.vknxworks.paperctrl.scan.engine.Geometry
import com.vknxworks.paperctrl.scan.engine.GrayImage
import com.vknxworks.paperctrl.scan.engine.Homography
import com.vknxworks.paperctrl.scan.engine.Pt
import com.vknxworks.paperctrl.scan.engine.QuadDetector
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
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

/**
 * Edge detection, perspective correction and filters, all on-device. The image maths is PaperCtrl's own plain
 * Kotlin (package `scan.engine`, unit-tested); the straightening itself is drawn by Android's own graphics.
 */
object ImageOps {

    val FULL_PAGE = listOf(Corner(0f, 0f), Corner(1f, 0f), Corner(1f, 1f), Corner(0f, 1f))

    private fun List<Pt>.toCorners() = map { Corner(it.x.toFloat(), it.y.toFloat()) }

    /**
     * Finds the largest four-sided shape (a sheet of paper) in a greyscale image, as corners in top-left,
     * top-right, bottom-right, bottom-left order. Null when nothing page-like is found.
     */
    fun detect(gray: GrayImage): List<Corner>? = QuadDetector.detect(gray)?.toCorners()

    /** The same for a camera frame's luma plane ([buffer] with [rowStride] bytes per row). */
    fun detect(buffer: ByteBuffer, width: Int, height: Int, rowStride: Int): List<Corner>? =
        detect(GrayImage.fromPlane(width, height, rowStride, QuadDetector.WORK_SIDE) { buffer.get(it).toInt() and 0xFF })

    fun detect(bitmap: Bitmap): List<Corner>? {
        val s = QuadDetector.WORK_SIDE.toFloat() / max(bitmap.width, bitmap.height)
        val small = if (s < 1f) {
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * s).roundToInt().coerceAtLeast(1), (bitmap.height * s).roundToInt().coerceAtLeast(1), true)
        } else bitmap
        val argb = IntArray(small.width * small.height)
        small.getPixels(argb, 0, small.width, 0, 0, small.width, small.height)
        val gray = GrayImage.fromArgb(small.width, small.height, argb)
        if (small !== bitmap) small.recycle()
        return detect(gray)
    }

    /**
     * Straightens the page inside [corners], applies [filter] and [rotation] (degrees clockwise), and
     * scales the result so its longest side is at most [maxSide] pixels.
     */
    fun render(src: Bitmap, corners: List<Corner>, filter: ScanFilter, rotation: Int, maxSide: Int): Bitmap {
        val sw = src.width.toDouble()
        val sh = src.height.toDouble()
        val p = corners.map { Pt(it.x * sw, it.y * sh) }
        val (outW, outH) = Geometry.outputSize(p, maxSide)
        var out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        val target = listOf(Pt(0.0, 0.0), Pt(outW.toDouble(), 0.0), Pt(outW.toDouble(), outH.toDouble()), Pt(0.0, outH.toDouble()))
        val h = if (corners == FULL_PAGE) null else Homography.between(p, target)
        val m = Matrix()
        if (h != null) m.setValues(FloatArray(9) { h.m[it].toFloat() })
        else m.setScale(outW / sw.toFloat(), outH / sh.toFloat())
        canvas.drawBitmap(src, m, paint)

        if (filter != ScanFilter.ORIGINAL) {
            val px = IntArray(outW * outH)
            out.getPixels(px, 0, outW, 0, 0, outW, outH)
            val filtered = when (filter) {
                ScanFilter.ENHANCED -> Filters.enhanced(px, outW, outH)
                ScanFilter.GREY -> Filters.grey(px)
                ScanFilter.BW -> Filters.blackAndWhite(px, outW, outH)
                ScanFilter.ORIGINAL -> px
            }
            out.setPixels(filtered, 0, outW, 0, 0, outW, outH)
        }
        val turn = ((rotation % 360) + 360) % 360
        if (turn != 0) {
            val r = Bitmap.createBitmap(out, 0, 0, outW, outH, Matrix().apply { postRotate(turn.toFloat()) }, true)
            if (r !== out) out.recycle()
            out = r
        }
        return out
    }
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
        Geometry.rotateFraction(Pt(c.x.toDouble(), c.y.toDouble()), rotationDegrees).let { Corner(it.x.toFloat(), it.y.toFloat()) }
    }

    fun toPoints(c: List<Corner>, w: Float, h: Float) = c.map { PointF(it.x * w, it.y * h) }
}
