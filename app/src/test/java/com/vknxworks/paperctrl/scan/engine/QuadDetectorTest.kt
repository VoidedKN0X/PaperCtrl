package com.vknxworks.paperctrl.scan.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Edge detection on synthetic photos: a light sheet of paper, seen at an angle, on a darker, noisy surface. */
class QuadDetectorTest {

    /** Draws [quad] (pixels) filled with [paper] on [background], with noise, shading and "text" lines. */
    private fun photo(
        w: Int, h: Int, quad: List<Pt>,
        paper: Int = 215, background: Int = 70, noise: Int = 12, text: Boolean = true, seed: Int = 1,
        shade: Boolean = false,
    ): GrayImage {
        val rnd = Random(seed)
        val img = GrayImage(w, h)
        for (y in 0 until h) for (x in 0 until w) {
            val inside = inside(quad, x + 0.5, y + 0.5)
            var v = if (inside) paper else background + ((x * 7 + y * 3) % 40) / 4
            if (inside && shade) v -= (60.0 * x / w).toInt()
            if (inside && text && (y / 6) % 3 == 0 && (x / 9) % 4 != 0) {
                // Rows of "words" well inside the page.
                val c = centroid(quad)
                if (kotlin.math.abs(x - c.x) < w * 0.15 && kotlin.math.abs(y - c.y) < h * 0.15) v = 40
            }
            img[x, y] = (v + rnd.nextInt(-noise, noise + 1)).coerceIn(0, 255)
        }
        return img
    }

    private fun centroid(q: List<Pt>) = Pt(q.sumOf { it.x } / q.size, q.sumOf { it.y } / q.size)

    private fun inside(q: List<Pt>, x: Double, y: Double): Boolean {
        var sign = 0
        for (i in q.indices) {
            val a = q[i]; val b = q[(i + 1) % q.size]
            val c = (b.x - a.x) * (y - a.y) - (b.y - a.y) * (x - a.x)
            val s = if (c > 0) 1 else if (c < 0) -1 else 0
            if (s != 0) { if (sign == 0) sign = s else if (s != sign) return false }
        }
        return true
    }

    private fun assertCorners(expected: List<Pt>, w: Int, h: Int, found: List<Pt>?, tol: Double = 0.025) {
        assertNotNull("no page found", found)
        val exp = Geometry.order(expected).map { Pt(it.x / w, it.y / h) }
        for (i in 0 until 4) {
            val d = Geometry.dist(exp[i], found!![i])
            assertTrue("corner $i off by $d: expected ${exp[i]}, got ${found[i]}", d < tol)
        }
    }

    @Test fun findsAnUprightPage() {
        val q = listOf(Pt(120.0, 80.0), Pt(520.0, 80.0), Pt(520.0, 640.0), Pt(120.0, 640.0))
        assertCorners(q, 640, 720, QuadDetector.detect(photo(640, 720, q)))
    }

    @Test fun findsAPageSeenAtAnAngle() {
        val q = listOf(Pt(180.0, 110.0), Pt(610.0, 160.0), Pt(560.0, 880.0), Pt(90.0, 800.0))
        assertCorners(q, 720, 960, QuadDetector.detect(photo(720, 960, q, seed = 7)))
    }

    @Test fun findsAPageWithShadowAndLowContrast() {
        val q = listOf(Pt(70.0, 60.0), Pt(420.0, 90.0), Pt(400.0, 560.0), Pt(50.0, 530.0))
        val img = photo(480, 640, q, paper = 190, background = 110, noise = 8, shade = true, seed = 3)
        assertCorners(q, 480, 640, QuadDetector.detect(img), tol = 0.03)
    }

    @Test fun cornersComeBackInOrderWhateverTheInputOrder() {
        val q = listOf(Pt(500.0, 600.0), Pt(100.0, 120.0), Pt(110.0, 610.0), Pt(530.0, 100.0))
        val found = QuadDetector.detect(photo(640, 720, Geometry.order(q)))
        assertCorners(q, 640, 720, found)
        // tl is the top-left one, br the bottom-right one.
        assertTrue(found!![0].x < found[1].x && found[0].y < found[3].y)
        assertTrue(found[2].x > found[3].x && found[2].y > found[1].y)
    }

    @Test fun noPageInPlainNoise() {
        val rnd = Random(42)
        val img = GrayImage(400, 300, IntArray(400 * 300) { 120 + rnd.nextInt(-20, 21) })
        assertNull(QuadDetector.detect(img))
    }

    @Test fun noPageWhenTheShapeIsTooSmall() {
        val q = listOf(Pt(300.0, 300.0), Pt(360.0, 300.0), Pt(360.0, 380.0), Pt(300.0, 380.0))
        assertNull(QuadDetector.detect(photo(640, 720, q, text = false)))
    }

    @Test fun noPageForACircle() {
        val w = 500; val h = 500
        val img = GrayImage(w, h)
        for (y in 0 until h) for (x in 0 until w) {
            val d = kotlin.math.hypot(x - 250.0, y - 250.0)
            img[x, y] = if (d < 200) 220 else 60
        }
        assertNull(QuadDetector.detect(img))
    }

    @Test fun otsuSplitsTwoLevels() {
        val px = IntArray(1000) { if (it < 600) 50 else 200 }
        val t = QuadDetector.otsu(px)
        assertTrue(t in 50 until 200)
    }

    @Test fun downscaleAveragesBlocks() {
        val img = GrayImage(4, 2, intArrayOf(0, 100, 200, 200, 100, 0, 200, 200))
        val s = img.downscale(2)
        assertEquals(2, s.width)
        assertEquals(1, s.height)
        assertEquals(50, s[0, 0])
        assertEquals(200, s[1, 0])
    }

    @Test fun cameraPlaneWithRowPadding() {
        // 4×2 frame stored with 6 bytes per row (2 bytes of padding that must be skipped).
        val bytes = byteArrayOf(10, 20, 30, 40, -1, -1, 50, 60, 70, 80, -1, -1)
        val g = GrayImage.fromPlane(4, 2, 6, 10) { bytes[it].toInt() and 0xFF }
        assertEquals(listOf(10, 20, 30, 40, 50, 60, 70, 80), g.px.toList())
    }
}
