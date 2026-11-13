package com.vknxworks.paperctrl.scan.engine

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/** A point in pixels (or in 0..1 fractions of an image, depending on where it's used). */
data class Pt(val x: Double, val y: Double)

object Geometry {

    fun dist(a: Pt, b: Pt) = hypot(a.x - b.x, a.y - b.y)

    /** Twice the signed area of triangle a-b-c (positive when counter-clockwise in y-up maths). */
    fun cross(a: Pt, b: Pt, c: Pt) = (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)

    /** Area of a simple polygon (shoelace formula), always positive. */
    fun area(poly: List<Pt>): Double {
        var s = 0.0
        for (i in poly.indices) {
            val a = poly[i]
            val b = poly[(i + 1) % poly.size]
            s += a.x * b.y - b.x * a.y
        }
        return abs(s) / 2
    }

    /** Four corners as top-left, top-right, bottom-right, bottom-left (image coordinates, y down). */
    fun order(p: List<Pt>): List<Pt> {
        require(p.size == 4)
        // Sort around the centre, then start at the corner closest to the top-left.
        val cx = p.sumOf { it.x } / 4
        val cy = p.sumOf { it.y } / 4
        val around = p.sortedBy { kotlin.math.atan2(it.y - cy, it.x - cx) } // clockwise on screen (y down)
        val start = around.indices.minBy { around[it].x + around[it].y }
        return List(4) { around[(start + it) % 4] }
    }

    /** True when the quad's corners (in order) make a convex shape. */
    fun isConvex(q: List<Pt>): Boolean {
        var sign = 0
        for (i in 0 until 4) {
            val c = cross(q[i], q[(i + 1) % 4], q[(i + 2) % 4])
            if (abs(c) < 1e-9) return false
            val s = if (c > 0) 1 else -1
            if (sign == 0) sign = s else if (s != sign) return false
        }
        return true
    }

    /** Convex hull (Andrew's monotone chain), counter-clockwise in y-up maths, without repeated points. */
    fun convexHull(points: List<Pt>): List<Pt> {
        val p = points.distinct().sortedWith(compareBy({ it.x }, { it.y }))
        if (p.size < 3) return p
        val lower = ArrayList<Pt>()
        for (pt in p) {
            while (lower.size >= 2 && cross(lower[lower.size - 2], lower[lower.size - 1], pt) <= 0) lower.removeAt(lower.size - 1)
            lower += pt
        }
        val upper = ArrayList<Pt>()
        for (pt in p.asReversed()) {
            while (upper.size >= 2 && cross(upper[upper.size - 2], upper[upper.size - 1], pt) <= 0) upper.removeAt(upper.size - 1)
            upper += pt
        }
        lower.removeAt(lower.size - 1)
        upper.removeAt(upper.size - 1)
        return lower + upper
    }

    /** Drops hull vertices that lie within [tolerance] of the line through their neighbours. */
    fun simplify(hull: List<Pt>, tolerance: Double): List<Pt> {
        val pts = hull.toMutableList()
        var changed = true
        while (changed && pts.size > 4) {
            changed = false
            var i = 0
            while (i < pts.size && pts.size > 4) {
                val a = pts[(i - 1 + pts.size) % pts.size]
                val b = pts[i]
                val c = pts[(i + 1) % pts.size]
                val len = dist(a, c)
                val d = if (len < 1e-9) 0.0 else abs(cross(a, c, b)) / len
                if (d < tolerance) { pts.removeAt(i); changed = true } else i++
            }
        }
        return pts
    }

    /** The largest quadrilateral whose corners are vertices of the convex polygon [hull]. */
    fun largestQuad(hull: List<Pt>): List<Pt>? {
        val n = hull.size
        if (n < 4) return null
        if (n == 4) return hull
        var best = -1.0
        var bestQ: List<Pt>? = null
        // Split by the diagonal i-k; on each side the farthest vertex makes the biggest triangle.
        for (i in 0 until n) for (k in i + 2 until n) {
            if (i == 0 && k == n - 1) continue
            var t1 = 0.0; var j1 = -1
            for (j in i + 1 until k) {
                val a = abs(cross(hull[i], hull[j], hull[k]))
                if (a > t1) { t1 = a; j1 = j }
            }
            var t2 = 0.0; var j2 = -1
            var j = (k + 1) % n
            while (j != i) {
                val a = abs(cross(hull[i], hull[j], hull[k]))
                if (a > t2) { t2 = a; j2 = j }
                j = (j + 1) % n
            }
            if (j1 < 0 || j2 < 0) continue
            if (t1 + t2 > best) {
                best = t1 + t2
                bestQ = listOf(hull[i], hull[j1], hull[k], hull[j2])
            }
        }
        return bestQ
    }

    /**
     * Size in pixels of the straightened page for [corners] (tl, tr, br, bl, in pixels): the longer of each pair
     * of opposite sides, scaled down so the longest side is at most [maxSide]. At least 16 px each way.
     */
    fun outputSize(corners: List<Pt>, maxSide: Int): Pair<Int, Int> {
        val w = max(dist(corners[0], corners[1]), dist(corners[3], corners[2]))
        val h = max(dist(corners[0], corners[3]), dist(corners[1], corners[2]))
        val scale = minOf(1.0, maxSide / max(max(w, h), 1.0))
        return (w * scale).roundToInt().coerceAtLeast(16) to (h * scale).roundToInt().coerceAtLeast(16)
    }

    /** Fractions (0..1) of a frame rotated by [degrees] clockwise to make it upright. */
    fun rotateFraction(p: Pt, degrees: Int): Pt = when (((degrees % 360) + 360) % 360) {
        90 -> Pt(1 - p.y, p.x)
        180 -> Pt(1 - p.x, 1 - p.y)
        270 -> Pt(p.y, 1 - p.x)
        else -> p
    }
}

/**
 * A perspective transform (3×3 matrix, last entry 1) that maps four points onto four others: the maths behind
 * straightening a photographed page.
 */
class Homography(val m: DoubleArray) {
    init { require(m.size == 9) }

    fun map(p: Pt): Pt {
        val w = m[6] * p.x + m[7] * p.y + m[8]
        return Pt((m[0] * p.x + m[1] * p.y + m[2]) / w, (m[3] * p.x + m[4] * p.y + m[5]) / w)
    }

    companion object {
        /** The transform taking each of [from] to the matching point of [to] (four each). Null if degenerate. */
        fun between(from: List<Pt>, to: List<Pt>): Homography? {
            require(from.size == 4 && to.size == 4)
            // Eight equations a·x = b for the eight unknowns h0..h7 (h8 = 1).
            val a = Array(8) { DoubleArray(8) }
            val b = DoubleArray(8)
            for (i in 0 until 4) {
                val (x, y) = from[i]
                val (u, v) = to[i]
                a[2 * i] = doubleArrayOf(x, y, 1.0, 0.0, 0.0, 0.0, -u * x, -u * y)
                b[2 * i] = u
                a[2 * i + 1] = doubleArrayOf(0.0, 0.0, 0.0, x, y, 1.0, -v * x, -v * y)
                b[2 * i + 1] = v
            }
            val h = solve(a, b) ?: return null
            return Homography(DoubleArray(9) { if (it < 8) h[it] else 1.0 })
        }

        /** Gaussian elimination with partial pivoting; null for a singular system. */
        internal fun solve(a: Array<DoubleArray>, b: DoubleArray): DoubleArray? {
            val n = b.size
            for (col in 0 until n) {
                val pivot = (col until n).maxBy { abs(a[it][col]) }
                if (abs(a[pivot][col]) < 1e-12) return null
                if (pivot != col) {
                    val t = a[pivot]; a[pivot] = a[col]; a[col] = t
                    val tb = b[pivot]; b[pivot] = b[col]; b[col] = tb
                }
                for (r in col + 1 until n) {
                    val f = a[r][col] / a[col][col]
                    if (f == 0.0) continue
                    for (c in col until n) a[r][c] -= f * a[col][c]
                    b[r] -= f * b[col]
                }
            }
            val x = DoubleArray(n)
            for (r in n - 1 downTo 0) {
                var s = b[r]
                for (c in r + 1 until n) s -= a[r][c] * x[c]
                x[r] = s / a[r][r]
            }
            return x
        }
    }
}
