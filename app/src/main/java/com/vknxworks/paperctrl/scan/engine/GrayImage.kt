package com.vknxworks.paperctrl.scan.engine

import kotlin.math.ceil
import kotlin.math.max

/** An 8-bit greyscale image, one value 0..255 per pixel, row by row. Plain Kotlin, so it can be unit-tested. */
class GrayImage(val width: Int, val height: Int, val px: IntArray = IntArray(width * height)) {
    init {
        require(width > 0 && height > 0 && px.size == width * height)
    }

    operator fun get(x: Int, y: Int): Int = px[y * width + x]
    operator fun set(x: Int, y: Int, v: Int) { px[y * width + x] = v }

    /** Box-averaged copy whose longest side is at most [maxSide] (the image itself when it's already small). */
    fun downscale(maxSide: Int): GrayImage {
        val k = ceil(max(width, height).toDouble() / maxSide).toInt()
        if (k <= 1) return this
        val w = (width / k).coerceAtLeast(1)
        val h = (height / k).coerceAtLeast(1)
        val out = IntArray(w * h)
        val n = k * k
        for (y in 0 until h) for (x in 0 until w) {
            var sum = 0
            val y0 = y * k
            val x0 = x * k
            for (dy in 0 until k) {
                val row = (y0 + dy) * width + x0
                for (dx in 0 until k) sum += px[row + dx]
            }
            out[y * w + x] = sum / n
        }
        return GrayImage(w, h, out)
    }

    /** 5×5 Gaussian blur (binomial 1-4-6-4-1, applied across and down). Edges repeat the border pixel. */
    fun blur5(): GrayImage {
        val tmp = IntArray(px.size)
        val out = IntArray(px.size)
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                fun p(i: Int) = px[row + i.coerceIn(0, width - 1)]
                tmp[row + x] = p(x - 2) + 4 * p(x - 1) + 6 * p(x) + 4 * p(x + 1) + p(x + 2)
            }
        }
        for (y in 0 until height) for (x in 0 until width) {
            fun p(j: Int) = tmp[j.coerceIn(0, height - 1) * width + x]
            out[y * width + x] = (p(y - 2) + 4 * p(y - 1) + 6 * p(y) + 4 * p(y + 1) + p(y + 2) + 128) / 256
        }
        return GrayImage(width, height, out)
    }

    companion object {
        /**
         * Reads the luma (Y) plane of a camera frame, averaged down so the longest side is at most [maxSide].
         * [byteAt] reads one byte of the plane (0..255), [rowStride] is the distance between rows.
         */
        inline fun fromPlane(width: Int, height: Int, rowStride: Int, maxSide: Int, byteAt: (Int) -> Int): GrayImage {
            val k = ceil(max(width, height).toDouble() / maxSide).toInt().coerceAtLeast(1)
            val w = (width / k).coerceAtLeast(1)
            val h = (height / k).coerceAtLeast(1)
            val out = IntArray(w * h)
            val n = k * k
            for (y in 0 until h) for (x in 0 until w) {
                var sum = 0
                for (dy in 0 until k) {
                    val row = (y * k + dy) * rowStride + x * k
                    for (dx in 0 until k) sum += byteAt(row + dx)
                }
                out[y * w + x] = sum / n
            }
            return GrayImage(w, h, out)
        }

        /** Luma of packed ARGB pixels (as from Bitmap.getPixels). */
        fun fromArgb(width: Int, height: Int, argb: IntArray): GrayImage =
            GrayImage(width, height, IntArray(argb.size) { luma(argb[it]) })

        fun luma(c: Int): Int = (77 * ((c shr 16) and 0xFF) + 150 * ((c shr 8) and 0xFF) + 29 * (c and 0xFF)) shr 8
    }
}
