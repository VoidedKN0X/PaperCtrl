package com.vknxworks.paperctrl.data.queue

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FilterOutputStream
import java.io.OutputStream
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/** How images become PDF pages. */
data class PdfQuality(
    /** Longest side of a page image, in pixels. 2339 px is an A4 page at 200 dpi. */
    val maxSidePx: Int = 2339,
    val jpegQuality: Int = 82,
    /** Resolution the page size is calculated from, so pages come out at their real paper size. */
    val dpi: Int = 200,
)

/** A page ready to go into a PDF: a JPEG and its size in pixels. */
class JpegPage(val jpeg: ByteArray, val width: Int, val height: Int)

/**
 * Turns images into PDF pages. Android's own PdfDocument stores images uncompressed (several MB per
 * page), so this writes a minimal PDF itself with each page embedded as a JPEG: small files that
 * Paperless then OCRs.
 */
object ImagePdf {

    /**
     * Decodes [file] (JPEG, PNG, HEIC, WebP…, with its EXIF rotation applied), scales it down to fit
     * [quality] and compresses it as JPEG. Transparent areas become white.
     */
    fun pageFromImage(file: File, quality: PdfQuality = PdfQuality()): JpegPage {
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val w = info.size.width
            val h = info.size.height
            val scale = quality.maxSidePx.toFloat() / max(w, h)
            if (scale < 1f) decoder.setTargetSize((w * scale).roundToInt().coerceAtLeast(1), (h * scale).roundToInt().coerceAtLeast(1))
        }
        return try {
            pageFromBitmap(bitmap, quality)
        } finally {
            bitmap.recycle()
        }
    }

    /** Compresses [bitmap] as a JPEG page. The bitmap is not recycled. */
    fun pageFromBitmap(bitmap: Bitmap, quality: PdfQuality = PdfQuality()): JpegPage {
        val opaque = if (bitmap.hasAlpha()) {
            Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888).also {
                Canvas(it).apply { drawColor(Color.WHITE); drawBitmap(bitmap, 0f, 0f, null) }
            }
        } else {
            bitmap
        }
        val out = ByteArrayOutputStream()
        opaque.compress(Bitmap.CompressFormat.JPEG, quality.jpegQuality, out)
        if (opaque !== bitmap) opaque.recycle()
        return JpegPage(out.toByteArray(), bitmap.width, bitmap.height)
    }

    /**
     * Writes a PDF of [count] pages, each produced in turn by [page] so only one
     * image is in memory at once.
     */
    fun write(target: File, count: Int, quality: PdfQuality = PdfQuality(), page: (Int) -> JpegPage) {
        require(count > 0)
        target.outputStream().buffered().use { raw ->
            val out = CountingStream(raw)
            val offsets = LongArray(3 + count * 3)
            fun obj(n: Int, body: () -> Unit) {
                offsets[n] = out.count
                out.ascii("$n 0 obj\n")
                body()
                out.ascii("\nendobj\n")
            }
            // Object numbers: 1 catalog, 2 page tree, then per page: page, image, content stream.
            fun pageObj(i: Int) = 3 + i * 3

            out.ascii("%PDF-1.4\n")
            out.write(byteArrayOf('%'.code.toByte(), 0xE2.toByte(), 0xE3.toByte(), 0xCF.toByte(), 0xD3.toByte(), '\n'.code.toByte()))
            obj(1) { out.ascii("<< /Type /Catalog /Pages 2 0 R >>") }
            obj(2) {
                val kids = (0 until count).joinToString(" ") { "${pageObj(it)} 0 R" }
                out.ascii("<< /Type /Pages /Kids [$kids] /Count $count >>")
            }
            for (i in 0 until count) {
                val p = page(i)
                val w = p.width * 72f / quality.dpi
                val h = p.height * 72f / quality.dpi
                val pw = "%.2f".format(Locale.ROOT, w)
                val ph = "%.2f".format(Locale.ROOT, h)
                val n = pageObj(i)
                obj(n) {
                    out.ascii(
                        "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $pw $ph] " +
                            "/Resources << /XObject << /Im0 ${n + 1} 0 R >> >> /Contents ${n + 2} 0 R >>",
                    )
                }
                obj(n + 1) {
                    out.ascii(
                        "<< /Type /XObject /Subtype /Image /Width ${p.width} /Height ${p.height} " +
                            "/ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${p.jpeg.size} >>\nstream\n",
                    )
                    out.write(p.jpeg)
                    out.ascii("\nendstream")
                }
                val content = "q $pw 0 0 $ph 0 0 cm /Im0 Do Q"
                obj(n + 2) { out.ascii("<< /Length ${content.length} >>\nstream\n$content\nendstream") }
            }
            val xref = out.count
            val total = 3 + count * 3
            out.ascii("xref\n0 $total\n0000000000 65535 f \n")
            for (n in 1 until total) out.ascii("%010d 00000 n \n".format(Locale.ROOT, offsets[n]))
            out.ascii("trailer\n<< /Size $total /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        }
    }

    private class CountingStream(out: OutputStream) : FilterOutputStream(out) {
        var count = 0L
            private set

        override fun write(b: Int) {
            out.write(b)
            count++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            out.write(b, off, len)
            count += len
        }

        fun ascii(s: String) = write(s.toByteArray(Charsets.US_ASCII))
    }
}
