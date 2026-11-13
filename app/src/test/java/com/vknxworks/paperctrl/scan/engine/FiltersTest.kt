package com.vknxworks.paperctrl.scan.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FiltersTest {
    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    private fun r(c: Int) = (c shr 16) and 0xFF
    private fun g(c: Int) = (c shr 8) and 0xFF
    private fun b(c: Int) = c and 0xFF

    /** A page with dark text strokes and a shadow from left (dim) to right (bright). */
    private fun shadedPage(w: Int, h: Int): IntArray = IntArray(w * h) { i ->
        val x = i % w; val y = i / w
        val light = 120 + 120 * x / w
        val text = (y / 10) % 3 == 0 && (x / 7) % 3 != 0
        val v = if (text) light / 4 else light
        rgb(v, (v * 0.95).toInt(), (v * 0.85).toInt())
    }

    @Test fun greyIsNeutralAndStretched() {
        val px = IntArray(100) { rgb(it + 50, 30, 200 - it) }
        val out = Filters.grey(px)
        out.forEach { assertTrue(r(it) == g(it) && g(it) == b(it)) }
        assertTrue(out.minOf { r(it) } <= 5)
        assertTrue(out.maxOf { r(it) } >= 250)
    }

    @Test fun blackAndWhiteKeepsTextAndIgnoresTheShadow() {
        val w = 300; val h = 200
        val out = Filters.blackAndWhite(shadedPage(w, h), w, h)
        out.forEach { assertTrue(r(it) == 0 || r(it) == 255) }
        // The paper is white on both the dim and the bright side; the text is black on both.
        for (x in listOf(20, 280)) {
            assertEquals(255, r(out[15 * w + x]))
            val textX = (x / 21) * 21 + 10
            assertEquals(0, r(out[2 * w + textX]))
        }
    }

    @Test fun enhancedEvensOutTheShadow() {
        val w = 320; val h = 240
        val out = Filters.enhanced(shadedPage(w, h), w, h)
        // Paper pixels (rows 10..19 have no text) on the dim and bright side end up nearly the same.
        val dim = out[15 * w + 10]
        val bright = out[15 * w + 310]
        assertTrue("dim ${r(dim)} vs bright ${r(bright)}", kotlin.math.abs(r(dim) - r(bright)) < 30)
        assertTrue(r(dim) > 200)
        // The paper's yellowish tint is gone: blue is close to red on paper.
        assertTrue(kotlin.math.abs(r(bright) - b(bright)) < 30)
        // Text stays dark.
        assertTrue(r(out[2 * w + 10]) < 120)
    }

    @Test fun percentilesOfAFlatImageStayFlat() {
        val px = IntArray(50) { 128 }
        assertEquals(128 to 128, Filters.percentiles(px, 0.01, 0.99))
        val out = Filters.grey(IntArray(50) { rgb(128, 128, 128) })
        out.forEach { assertEquals(128, r(it)) }
    }
}
