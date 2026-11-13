package com.vknxworks.paperctrl.scan.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The perspective maths behind straightening a page, and the helpers around it. */
class GeometryTest {
    private fun near(a: Pt, b: Pt, tol: Double = 1e-6) =
        assertTrue("expected $a, got $b", Geometry.dist(a, b) < tol)

    private val photo = listOf(Pt(120.0, 80.0), Pt(900.0, 140.0), Pt(860.0, 1210.0), Pt(60.0, 1120.0))
    private val page = listOf(Pt(0.0, 0.0), Pt(800.0, 0.0), Pt(800.0, 1100.0), Pt(0.0, 1100.0))

    @Test fun homographyMapsTheFourCorners() {
        val h = Homography.between(photo, page)
        assertNotNull(h)
        for (i in 0 until 4) near(page[i], h!!.map(photo[i]))
    }

    @Test fun homographyKeepsStraightLinesAndMapsTheDiagonalsCrossing() {
        val h = Homography.between(photo, page)!!
        // Where the photo's diagonals cross is the page's centre (perspective keeps incidence).
        val p = photo
        val d = (p[0].x - p[2].x) * (p[1].y - p[3].y) - (p[0].y - p[2].y) * (p[1].x - p[3].x)
        val a = p[0].x * p[2].y - p[0].y * p[2].x
        val b = p[1].x * p[3].y - p[1].y * p[3].x
        val cross = Pt((a * (p[1].x - p[3].x) - (p[0].x - p[2].x) * b) / d, (a * (p[1].y - p[3].y) - (p[0].y - p[2].y) * b) / d)
        near(Pt(400.0, 550.0), h.map(cross), 1e-6)
        // A point a third along the top edge stays on the top edge.
        val third = Pt(p[0].x + (p[1].x - p[0].x) / 3, p[0].y + (p[1].y - p[0].y) / 3)
        assertEquals(0.0, h.map(third).y, 1e-6)
    }

    @Test fun inverseUndoesTheTransform() {
        val fwd = Homography.between(photo, page)!!
        val back = Homography.between(page, photo)!!
        for (pt in listOf(Pt(10.0, 20.0), Pt(400.0, 700.0), Pt(790.0, 1090.0))) near(pt, fwd.map(back.map(pt)), 1e-6)
    }

    @Test fun identityAndScale() {
        val sq = listOf(Pt(0.0, 0.0), Pt(1.0, 0.0), Pt(1.0, 1.0), Pt(0.0, 1.0))
        val id = Homography.between(sq, sq)!!
        near(Pt(0.3, 0.7), id.map(Pt(0.3, 0.7)))
        val big = Homography.between(sq, sq.map { Pt(it.x * 200, it.y * 100) })!!
        near(Pt(60.0, 70.0), big.map(Pt(0.3, 0.7)))
    }

    @Test fun collinearCornersHaveNoTransform() {
        val line = listOf(Pt(0.0, 0.0), Pt(1.0, 1.0), Pt(2.0, 2.0), Pt(3.0, 3.0))
        assertNull(Homography.between(line, page))
    }

    @Test fun orderGivesTopLeftTopRightBottomRightBottomLeft() {
        val shuffled = listOf(photo[2], photo[0], photo[3], photo[1])
        assertEquals(photo, Geometry.order(shuffled))
        // Strongly rotated page: still a consistent clockwise order from the top-left-most corner.
        val tilted = listOf(Pt(50.0, 10.0), Pt(90.0, 50.0), Pt(50.0, 90.0), Pt(10.0, 50.0))
        val o = Geometry.order(tilted.reversed())
        assertTrue(Geometry.isConvex(o))
        assertEquals(4, o.toSet().size)
    }

    @Test fun outputSizeUsesTheLongerSidesAndTheLimit() {
        assertEquals(800 to 1100, Geometry.outputSize(page, 2000))
        assertEquals(800 to 1100, Geometry.outputSize(page, 1100))
        assertEquals(400 to 550, Geometry.outputSize(page, 550))
        val (w, h) = Geometry.outputSize(photo, 4000)
        assertEquals(Geometry.dist(photo[0], photo[1]).let { kotlin.math.max(it, Geometry.dist(photo[3], photo[2])) }, w.toDouble(), 0.5)
        assertTrue(h > w)
        assertEquals(16 to 16, Geometry.outputSize(listOf(Pt(0.0, 0.0), Pt(1.0, 0.0), Pt(1.0, 1.0), Pt(0.0, 1.0)), 100))
    }

    @Test fun rotatingFractionsFourTimesIsIdentity() {
        val p = Pt(0.2, 0.7)
        near(Pt(0.3, 0.2), Geometry.rotateFraction(p, 90))
        near(Pt(0.8, 0.3), Geometry.rotateFraction(p, 180))
        near(Pt(0.7, 0.8), Geometry.rotateFraction(p, 270))
        var q = p
        repeat(4) { q = Geometry.rotateFraction(q, 90) }
        near(p, q)
        near(p, Geometry.rotateFraction(p, -360))
    }

    @Test fun hullAndLargestQuad() {
        // A square with extra points along its sides and inside: the hull is the square.
        val pts = mutableListOf<Pt>()
        for (i in 0..10) { pts += Pt(i * 10.0, 0.0); pts += Pt(i * 10.0, 100.0); pts += Pt(0.0, i * 10.0); pts += Pt(100.0, i * 10.0) }
        pts += Pt(50.0, 50.0)
        val hull = Geometry.simplify(Geometry.convexHull(pts), 0.5)
        assertEquals(4, hull.size)
        // An octagon-like hull (cut corners): the largest quad uses four of its vertices.
        val oct = listOf(Pt(10.0, 0.0), Pt(90.0, 0.0), Pt(100.0, 10.0), Pt(100.0, 90.0), Pt(90.0, 100.0), Pt(10.0, 100.0), Pt(0.0, 90.0), Pt(0.0, 10.0))
        val q = Geometry.largestQuad(Geometry.convexHull(oct))!!
        assertEquals(4, q.size)
        assertTrue(Geometry.area(q) > 0.8 * Geometry.area(Geometry.convexHull(oct)))
        assertTrue(Geometry.isConvex(Geometry.order(q)))
        assertFalse(Geometry.isConvex(listOf(Pt(0.0, 0.0), Pt(10.0, 10.0), Pt(10.0, 0.0), Pt(0.0, 10.0))))
    }
}
