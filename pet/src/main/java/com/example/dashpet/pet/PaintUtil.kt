package com.example.dashpet.pet

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

/** 시안의 `rgba(r,g,b,a)` 문자열과 같은 값. */
internal fun rgba(r: Int, g: Int, b: Int, a: Float): Color = Color(r / 255f, g / 255f, b / 255f, a)

/** 시안 `mix(a, b, t)`: sRGB 성분을 선형 보간하고 정수로 반올림한다(Compose `lerp` 는 Oklab 이라 쓰지 않는다). */
internal fun mixColor(a: Color, b: Color, t: Float): Color {
    fun ch(x: Float, y: Float) = (x * 255f + (y * 255f - x * 255f) * t).roundToInt().coerceIn(0, 255) / 255f
    return Color(ch(a.red, b.red), ch(a.green, b.green), ch(a.blue, b.blue), 1f)
}

/** 시안 `lum(hex)`. */
internal fun luminance(c: Color): Float = 0.299f * c.red + 0.587f * c.green + 0.114f * c.blue

private fun Float.toDegrees(): Float = (this * 180.0 / PI).toFloat()

/**
 * 캔버스 `arcTo(x1, y1, x2, y2, r)` 와 같은 접선 호. 현재 점 [p0] 에서 [p1] 쪽으로 가다가 [p1] 모서리를
 * 반지름 [r] 로 둥글리고 [p2] 쪽으로 이어진다. 끝난 점(두 번째 접점)을 돌려준다.
 */
internal fun Path.arcToTangent(p0: Offset, p1: Offset, p2: Offset, r: Float): Offset {
    val v1x = p0.x - p1.x
    val v1y = p0.y - p1.y
    val v2x = p2.x - p1.x
    val v2y = p2.y - p1.y
    val l1 = hypot(v1x, v1y)
    val l2 = hypot(v2x, v2y)
    val cross = v1x * v2y - v1y * v2x
    if (r <= 0f || l1 == 0f || l2 == 0f || abs(cross) < 1e-6f) {
        lineTo(p1.x, p1.y)
        return p1
    }
    val dot = v1x * v2x + v1y * v2y
    val theta = atan2(abs(cross), dot)
    val d = r / tan(theta / 2f)
    val t1 = Offset(p1.x + v1x / l1 * d, p1.y + v1y / l1 * d)
    val t2 = Offset(p1.x + v2x / l2 * d, p1.y + v2y / l2 * d)
    val bx = v1x / l1 + v2x / l2
    val by = v1y / l1 + v2y / l2
    val bl = hypot(bx, by)
    val dist = r / sin(theta / 2f)
    val c = Offset(p1.x + bx / bl * dist, p1.y + by / bl * dist)
    lineTo(t1.x, t1.y)
    val start = atan2(t1.y - c.y, t1.x - c.x)
    val end = atan2(t2.y - c.y, t2.x - c.x)
    var sweep = end - start
    while (sweep > PI) sweep -= (2 * PI).toFloat()
    while (sweep <= -PI) sweep += (2 * PI).toFloat()
    arcTo(Rect(c.x - r, c.y - r, c.x + r, c.y + r), start.toDegrees(), sweep.toDegrees(), false)
    return t2
}

/** 시안 `roundedPoly(p, r)`: 각 모서리를 반지름 [r] 로 둥글린 다각형 경로. */
internal fun roundedPolygon(points: List<Offset>, r: Float): Path {
    val n = points.size
    val path = Path()
    var cur = Offset((points[n - 1].x + points[0].x) / 2f, (points[n - 1].y + points[0].y) / 2f)
    path.moveTo(cur.x, cur.y)
    for (i in 0 until n) cur = path.arcToTangent(cur, points[i], points[(i + 1) % n], r)
    path.close()
    return path
}

/** 중심 ([cx], [cy]), 반지름 [r] 의 호를 그리는 경로(캔버스 `arc`, 시계 방향). 각도는 라디안. */
internal fun arcPath(cx: Float, cy: Float, r: Float, startRad: Float, endRad: Float): Path =
    Path().apply {
        arcTo(Rect(cx - r, cy - r, cx + r, cy + r), startRad.toDegrees(), (endRad - startRad).toDegrees(), true)
    }

/**
 * `shadowBlur` 글로우의 근사(research R7): 넓은 반투명 선 두 겹 + 좁은 밝은 선.
 * [blur] 는 시안의 `shadowBlur` 값이다.
 */
internal fun DrawScope.glowStroke(
    path: Path,
    core: Color,
    glow: Color,
    width: Float,
    blur: Float,
    alpha: Float = 1f,
    cap: StrokeCap = StrokeCap.Round,
    join: StrokeJoin = StrokeJoin.Round,
) {
    drawPath(path, glow.copy(alpha = glow.alpha * 0.16f * alpha), style = Stroke(width + blur * 1.2f, cap = cap, join = join))
    drawPath(path, glow.copy(alpha = glow.alpha * 0.32f * alpha), style = Stroke(width + blur * 0.6f, cap = cap, join = join))
    drawPath(path, core.copy(alpha = core.alpha * alpha), style = Stroke(width, cap = cap, join = join))
}
