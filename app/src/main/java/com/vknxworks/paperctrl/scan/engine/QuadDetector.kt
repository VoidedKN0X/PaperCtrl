package com.vknxworks.paperctrl.scan.engine

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Finds a sheet of paper in a photo, entirely in plain Kotlin (no OpenCV, no ML Kit):
 *
 * 1. scale down to at most [WORK_SIDE] pixels and blur;
 * 2. Canny-like edges: Sobel gradients, thinning to the strongest pixel across each edge, and two thresholds
 *    that follow weak edges only where they touch strong ones;
 * 3. candidates: every connected edge outline, and the largest bright area (paper is usually lighter than
 *    what it lies on, found with Otsu's threshold);
 * 4. each candidate's convex hull gives its largest four-cornered shape, which must be big enough, roughly
 *    rectangular in perspective, fill the hull, and lie on real edges for most of its outline.
 *
 * The best-scoring quad is returned as corners (fractions 0..1 of the image) in tl, tr, br, bl order.
 */
object QuadDetector {
    const val WORK_SIDE = 360

    /** A page fills at least this much of the frame. */
    private const val MIN_AREA = 0.15
    /** Corners closer to straight than this, or sharper, aren't a page (degrees). */
    private const val MIN_ANGLE = 35.0
    private const val MAX_ANGLE = 145.0

    fun detect(image: GrayImage): List<Pt>? {
        val small = image.downscale(WORK_SIDE).blur5()
        val w = small.width
        val h = small.height
        if (w < 16 || h < 16) return null
        val edges = cannyLike(small)
        val near = dilate(edges, w, h)

        val candidates = ArrayList<List<Pt>>()
        components(near, w, h, minPixels = (w + h) / 2).forEach { candidates += it }
        brightRegion(small)?.let { candidates += it }

        var best: List<Pt>? = null
        var bestScore = 0.0
        val frame = w.toDouble() * h
        for (outline in candidates) {
            val hull = Geometry.simplify(Geometry.convexHull(outline), 1.0)
            val quad = Geometry.largestQuad(hull)?.let(Geometry::order) ?: continue
            val area = Geometry.area(quad)
            if (area < frame * MIN_AREA) continue
            if (!Geometry.isConvex(quad) || !anglesOk(quad)) continue
            // A page outline is (nearly) its own hull: a much bigger hull means something else is attached.
            if (area < Geometry.area(hull) * 0.85) continue
            val support = edgeSupport(quad, near, w, h)
            if (support < 0.6) continue
            val score = area * support * support
            if (score > bestScore) { bestScore = score; best = quad }
        }
        val q = best ?: return null
        return q.map { Pt(((it.x + 0.5) / w).coerceIn(0.0, 1.0), ((it.y + 0.5) / h).coerceIn(0.0, 1.0)) }
    }

    /** Every interior angle between [MIN_ANGLE] and [MAX_ANGLE]: a page seen at an angle, not a sliver. */
    private fun anglesOk(q: List<Pt>): Boolean = (0 until 4).all { i ->
        val p = q[(i + 3) % 4]; val c = q[i]; val n = q[(i + 1) % 4]
        val ax = p.x - c.x; val ay = p.y - c.y; val bx = n.x - c.x; val by = n.y - c.y
        val len = hypot(ax, ay) * hypot(bx, by)
        if (len < 1e-9) return false
        val deg = acos(((ax * bx + ay * by) / len).coerceIn(-1.0, 1.0)) * 180 / PI
        deg in MIN_ANGLE..MAX_ANGLE
    }

    /** Share of points along the quad's sides that lie on (or next to) an edge. */
    internal fun edgeSupport(q: List<Pt>, near: BooleanArray, w: Int, h: Int): Double {
        var on = 0
        var total = 0
        for (i in 0 until 4) {
            val a = q[i]; val b = q[(i + 1) % 4]
            val steps = max(1, Geometry.dist(a, b).toInt())
            for (s in 0..steps) {
                val t = s.toDouble() / steps
                val x = (a.x + (b.x - a.x) * t).toInt().coerceIn(0, w - 1)
                val y = (a.y + (b.y - a.y) * t).toInt().coerceIn(0, h - 1)
                total++
                if (near[y * w + x]) on++
            }
        }
        return if (total == 0) 0.0 else on.toDouble() / total
    }

    /** Canny-like edge map: Sobel, non-maximum suppression, hysteresis with thresholds from the image itself. */
    internal fun cannyLike(g: GrayImage): BooleanArray {
        val w = g.width; val h = g.height
        val mag = FloatArray(w * h)
        val dir = ByteArray(w * h)
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            val tl = g.px[i - w - 1]; val tc = g.px[i - w]; val tr = g.px[i - w + 1]
            val ml = g.px[i - 1]; val mr = g.px[i + 1]
            val bl = g.px[i + w - 1]; val bc = g.px[i + w]; val br = g.px[i + w + 1]
            val gx = (tr + 2 * mr + br) - (tl + 2 * ml + bl)
            val gy = (bl + 2 * bc + br) - (tl + 2 * tc + tr)
            mag[i] = sqrt((gx * gx + gy * gy).toFloat())
            // 0: horizontal gradient (vertical edge), 1: 45°, 2: vertical gradient, 3: 135°.
            val ax = abs(gx); val ay = abs(gy)
            dir[i] = when {
                ay * 5 < ax * 2 -> 0
                ax * 5 < ay * 2 -> 2
                (gx > 0) == (gy > 0) -> 1
                else -> 3
            }
        }
        val thin = FloatArray(w * h)
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            val m = mag[i]
            if (m == 0f) continue
            val (a, b) = when (dir[i].toInt()) {
                0 -> mag[i - 1] to mag[i + 1]
                2 -> mag[i - w] to mag[i + w]
                1 -> mag[i - w - 1] to mag[i + w + 1]
                else -> mag[i - w + 1] to mag[i + w - 1]
            }
            if (m >= a && m >= b) thin[i] = m
        }
        // Thresholds from the strongest thinned edges, with a floor so flat, noisy frames find nothing.
        val nonZero = thin.filter { it > 0f }.sorted()
        if (nonZero.isEmpty()) return BooleanArray(w * h)
        val high = max(nonZero[(nonZero.size * 0.85).toInt().coerceAtMost(nonZero.size - 1)], 60f)
        val low = high * 0.4f
        val out = BooleanArray(w * h)
        val stack = IntArray(w * h)
        var sp = 0
        for (i in thin.indices) if (thin[i] >= high && !out[i]) {
            out[i] = true
            stack[sp++] = i
            while (sp > 0) {
                val c = stack[--sp]
                val cx = c % w; val cy = c / w
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = cx + dx; val ny = cy + dy
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                    val n = ny * w + nx
                    if (!out[n] && thin[n] >= low) { out[n] = true; stack[sp++] = n }
                }
            }
        }
        return out
    }

    /** Edge map grown by one pixel, so small gaps in an outline close. */
    internal fun dilate(e: BooleanArray, w: Int, h: Int): BooleanArray {
        val out = BooleanArray(e.size)
        for (y in 0 until h) for (x in 0 until w) {
            if (!e[y * w + x]) continue
            for (dy in -1..1) for (dx in -1..1) {
                val nx = x + dx; val ny = y + dy
                if (nx in 0 until w && ny in 0 until h) out[ny * w + nx] = true
            }
        }
        return out
    }

    /**
     * Outlines of connected pixel groups in [mask] with at least [minPixels] pixels. Each comes back as the
     * leftmost and rightmost pixel of every row (enough for its convex hull).
     */
    internal fun components(mask: BooleanArray, w: Int, h: Int, minPixels: Int, eight: Boolean = true): List<List<Pt>> {
        val seen = BooleanArray(mask.size)
        val stack = IntArray(mask.size)
        val result = ArrayList<List<Pt>>()
        val rowMin = IntArray(h)
        val rowMax = IntArray(h)
        for (start in mask.indices) {
            if (!mask[start] || seen[start]) continue
            rowMin.fill(Int.MAX_VALUE); rowMax.fill(-1)
            var count = 0
            var sp = 0
            stack[sp++] = start
            seen[start] = true
            while (sp > 0) {
                val c = stack[--sp]
                val cx = c % w; val cy = c / w
                count++
                if (cx < rowMin[cy]) rowMin[cy] = cx
                if (cx > rowMax[cy]) rowMax[cy] = cx
                for (dy in -1..1) for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    if (!eight && dx != 0 && dy != 0) continue
                    val nx = cx + dx; val ny = cy + dy
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                    val n = ny * w + nx
                    if (mask[n] && !seen[n]) { seen[n] = true; stack[sp++] = n }
                }
            }
            if (count < minPixels) continue
            val pts = ArrayList<Pt>()
            for (y in 0 until h) if (rowMax[y] >= 0) {
                pts += Pt(rowMin[y].toDouble(), y.toDouble())
                if (rowMax[y] != rowMin[y]) pts += Pt(rowMax[y].toDouble(), y.toDouble())
            }
            result += pts
        }
        return result
    }

    /** The largest area lighter than Otsu's threshold (the paper), as its row outline; null if there's none. */
    internal fun brightRegion(g: GrayImage): List<Pt>? {
        val t = otsu(g.px)
        val mask = BooleanArray(g.px.size) { g.px[it] > t }
        val parts = components(mask, g.width, g.height, minPixels = (g.width * g.height * MIN_AREA).toInt(), eight = false)
        return parts.maxByOrNull { it.size }
    }

    /** Otsu's threshold: the grey level that best splits the image into two groups. */
    internal fun otsu(px: IntArray): Int {
        val hist = IntArray(256)
        for (v in px) hist[v.coerceIn(0, 255)]++
        val total = px.size.toDouble()
        var sumAll = 0.0
        for (i in 0..255) sumAll += i * hist[i].toDouble()
        var sumB = 0.0
        var wB = 0.0
        var best = 0.0
        var threshold = 127
        for (t in 0..255) {
            wB += hist[t]
            if (wB == 0.0) continue
            val wF = total - wB
            if (wF == 0.0) break
            sumB += t * hist[t].toDouble()
            val mB = sumB / wB
            val mF = (sumAll - sumB) / wF
            val between = wB * wF * (mB - mF) * (mB - mF)
            if (between > best) { best = between; threshold = t }
        }
        return threshold
    }
}
