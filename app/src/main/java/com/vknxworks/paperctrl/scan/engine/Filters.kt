package com.vknxworks.paperctrl.scan.engine

import kotlin.math.max
import kotlin.math.min

/**
 * Page filters on packed ARGB pixels (as from Bitmap.getPixels), in plain Kotlin. Every function returns a new
 * array and leaves its input alone.
 */
object Filters {

    /** Grey: luma, with the darkest and lightest half percent stretched to black and white. */
    fun grey(argb: IntArray): IntArray {
        val luma = IntArray(argb.size) { GrayImage.luma(argb[it]) }
        val (lo, hi) = percentiles(luma, 0.005, 0.995)
        return IntArray(argb.size) { gray(stretch(luma[it], lo, hi)) }
    }

    /**
     * Black and white: each pixel against the mean of the block around it (adaptive threshold), so shadows
     * and uneven light don't turn into black areas. [offset] keeps faint paper texture white.
     */
    fun blackAndWhite(argb: IntArray, w: Int, h: Int, offset: Int = 12): IntArray {
        val luma = GrayImage(w, h, IntArray(argb.size) { GrayImage.luma(argb[it]) })
        val g = if (w >= 8 && h >= 8) blur3(luma) else luma
        val block = (max(w, h) / 60).let { if (it % 2 == 0) it + 1 else it }.coerceIn(15, 61)
        val r = block / 2
        // Summed-area table: the sum of any block in four look-ups.
        val sat = LongArray((w + 1) * (h + 1))
        for (y in 0 until h) {
            var row = 0L
            for (x in 0 until w) {
                row += g.px[y * w + x]
                sat[(y + 1) * (w + 1) + x + 1] = sat[y * (w + 1) + x + 1] + row
            }
        }
        val out = IntArray(argb.size)
        for (y in 0 until h) for (x in 0 until w) {
            val x0 = max(0, x - r); val x1 = min(w - 1, x + r)
            val y0 = max(0, y - r); val y1 = min(h - 1, y + r)
            val sum = sat[(y1 + 1) * (w + 1) + x1 + 1] - sat[y0 * (w + 1) + x1 + 1] - sat[(y1 + 1) * (w + 1) + x0] + sat[y0 * (w + 1) + x0]
            val mean = sum / ((x1 - x0 + 1) * (y1 - y0 + 1))
            out[y * w + x] = gray(if (g.px[y * w + x] > mean - offset) 255 else 0)
        }
        return out
    }

    /**
     * Enhanced colour: removes shadows and the paper's tint by dividing each channel by its estimated background
     * (the brightest values nearby, smoothed), then stretches the contrast.
     */
    fun enhanced(argb: IntArray, w: Int, h: Int): IntArray {
        val cell = 16
        val gw = (w + cell - 1) / cell
        val gh = (h + cell - 1) / cell
        val out = IntArray(argb.size) { 0xFF shl 24 }
        // One channel at a time, in one reused buffer, to keep memory low on full-size pages.
        val dst = IntArray(argb.size)
        for (shift in intArrayOf(16, 8, 0)) {
            // Background per cell: the brightest pixel in it, grown and smoothed over the neighbouring cells.
            val grid = IntArray(gw * gh)
            for (y in 0 until h) for (x in 0 until w) {
                val v = (argb[y * w + x] shr shift) and 0xFF
                val gi = (y / cell) * gw + x / cell
                if (v > grid[gi]) grid[gi] = v
            }
            val grown = IntArray(grid.size) { i ->
                val gx = i % gw; val gy = i / gw
                var m = 0
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = gx + dx; val ny = gy + dy
                    if (nx in 0 until gw && ny in 0 until gh) m = max(m, grid[ny * gw + nx])
                }
                m
            }
            val smooth = FloatArray(grid.size) { i ->
                val gx = i % gw; val gy = i / gw
                var s = 0; var n = 0
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = gx + dx; val ny = gy + dy
                    if (nx in 0 until gw && ny in 0 until gh) { s += grown[ny * gw + nx]; n++ }
                }
                s.toFloat() / n
            }
            for (y in 0 until h) {
                // Bilinear between cell centres.
                val fy = ((y + 0.5f) / cell - 0.5f).coerceIn(0f, (gh - 1).toFloat())
                val y0 = fy.toInt(); val y1 = min(y0 + 1, gh - 1); val ty = fy - y0
                for (x in 0 until w) {
                    val fx = ((x + 0.5f) / cell - 0.5f).coerceIn(0f, (gw - 1).toFloat())
                    val x0 = fx.toInt(); val x1 = min(x0 + 1, gw - 1); val tx = fx - x0
                    val top = smooth[y0 * gw + x0] * (1 - tx) + smooth[y0 * gw + x1] * tx
                    val bottom = smooth[y1 * gw + x0] * (1 - tx) + smooth[y1 * gw + x1] * tx
                    val bg = max(top * (1 - ty) + bottom * ty, 16f)
                    val v = (argb[y * w + x] shr shift) and 0xFF
                    dst[y * w + x] = min(255f, v * 255f / bg).toInt()
                }
            }
            val (lo, hi) = percentiles(dst, 0.005, 1.0)
            for (i in dst.indices) out[i] = out[i] or (stretch(dst[i], lo, hi) shl shift)
        }
        return out
    }

    private fun blur3(g: GrayImage): GrayImage {
        val w = g.width; val h = g.height
        val out = IntArray(g.px.size)
        for (y in 0 until h) for (x in 0 until w) {
            var s = 0; var n = 0
            for (dy in -1..1) for (dx in -1..1) {
                val nx = x + dx; val ny = y + dy
                if (nx in 0 until w && ny in 0 until h) {
                    val wgt = if (dx == 0 && dy == 0) 4 else if (dx == 0 || dy == 0) 2 else 1
                    s += g.px[ny * w + nx] * wgt; n += wgt
                }
            }
            out[y * w + x] = s / n
        }
        return GrayImage(w, h, out)
    }

    internal fun percentiles(v: IntArray, low: Double, high: Double): Pair<Int, Int> {
        val hist = IntArray(256)
        for (x in v) hist[x.coerceIn(0, 255)]++
        fun at(q: Double): Int {
            val target = (v.size * q).toLong().coerceAtMost(v.size - 1L)
            var acc = 0L
            for (i in 0..255) { acc += hist[i]; if (acc > target) return i }
            return 255
        }
        return at(low) to at(high)
    }

    private fun stretch(v: Int, lo: Int, hi: Int): Int =
        if (hi - lo < 8) v else ((v - lo) * 255 / (hi - lo)).coerceIn(0, 255)

    private fun gray(v: Int) = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
}
