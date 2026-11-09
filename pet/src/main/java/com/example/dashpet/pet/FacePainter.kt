package com.example.dashpet.pet

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import com.example.dashpet.engine.Mood
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private val Pi = PI.toFloat()
private val LightCore = Color(0xFFFFF6DC)
private val LightGlow = Color(0xFFFFD27A)
private val WinkCore = Color(0xFFFFE2A0)
private val BrowCore = Color(0xFFFFF4D6)

/**
 * 얼굴 전체(시안 `drawPet` 의 볼터치, 눈, 입). 풀 모션의 편안/기쁨/찌그러짐/어지러움/깜짝만 그린다.
 * 눈 깜빡임 일정과 떨림은 물리가 정한 값을 읽는다(난수를 쓰지 않는다). [lit] 쪽 윙크(방향지시등)는 US4 에서 연결한다.
 */
internal fun DrawScope.drawFace(
    mood: Mood,
    base: Color,
    personality: Personality,
    g: PetGeometry,
    ph: PetPhysicsState,
    t: Float,
) {
    val e = g.e
    val lean = g.lean
    val ew = g.rf * 0.44f * (0.85f + 0.15f * ph.sx) * (1f + 0.15f * e)
    val eh = g.rf * 0.29f * (0.8f + 0.2f * ph.sy) * (1f - 0.3f * e)
    val ey = g.cy - g.ry * (0.18f - 0.06f * e) - g.ry * 0.14f * lean * (1f - e)
    val ex = g.rx * 0.4f + (g.rf * 0.55f - g.rx * 0.4f) * e

    // 눈 깜빡임: 편안할 때만, 0.16초 동안 sin 곡선으로 감겼다 뜬다.
    var blink = 0f
    if (mood == Mood.IDLE || mood == Mood.AMBIENT) {
        val bt = t - ph.blinkStartSec
        if (bt >= 0f && bt < 0.16f) blink = sin(bt / 0.16f * Pi)
    }
    val look = ph.lat?.let { it.dir * 0.9f } ?: (ph.vx / 900f).coerceIn(-1f, 1f)

    // 볼터치: 급가속의 G-포스 표정이 우선, 그다음 애교 성격 또는 기쁨
    if (lean > 0.3f) {
        val c = rgba(255, 120, 150, 0.3f * lean)
        for (sd in intArrayOf(-1, 1)) {
            val rx = ew * 0.36f
            val ry = eh * 0.36f
            drawOval(c, Offset(g.cx + sd * ex * 1.28f - rx, ey + eh * 1.05f - ry), Size(rx * 2, ry * 2))
        }
    } else if (personality == Personality.CUTE || mood == Mood.HAPPY) {
        val c = rgba(255, 120, 150, if (mood == Mood.HAPPY) 0.4f else 0.3f)
        for (sd in intArrayOf(-1, 1)) {
            val rx = ew * 0.28f
            val ry = eh * 0.22f
            drawOval(c, Offset(g.cx + sd * ex * 1.32f - rx, ey + eh * 0.95f - ry), Size(rx * 2, ry * 2))
        }
    }

    drawHeadlight(g.cx - ex, ey, ew, eh, -1, mood, t, look, blink, base, wink = false, signal = false)
    drawHeadlight(g.cx + ex, ey, ew, eh, 1, mood, t, look, blink, base, wink = false, signal = false)
    drawMouth(g.cx, ey + eh * 1.15f + eh * 0.25f * e, ew * 0.62f * (1f + 0.35f * e), eh, mood, t, lean)
}

/** 시안 `glow(x, y, r, a)`: 흰색에서 따뜻한 노랑으로 번지는 방사형 빛(이미 그라데이션이라 그대로). */
internal fun DrawScope.drawLightGlow(x: Float, y: Float, r: Float, a: Float) {
    val brush = Brush.radialGradient(
        0f to rgba(255, 255, 255, a),
        0.35f to rgba(255, 236, 190, a * 0.9f),
        1f to rgba(255, 210, 122, 0f),
        center = Offset(x, y),
        radius = r,
    )
    drawCircle(brush, r, Offset(x, y))
}

/** 시안 `headlight`: 하우징, 감정별 눈빛, 눈꺼풀, LED 눈썹. [side] -1 은 왼쪽 눈, +1 은 오른쪽 눈. */
private fun DrawScope.drawHeadlight(
    x: Float, y: Float, w: Float, h: Float, side: Int,
    mood: Mood, t: Float, look: Float, blink: Float, lid: Color,
    wink: Boolean, signal: Boolean,
) {
    withTransform({
        translate(x, y)
        scale(side.toFloat(), 1f, Offset.Zero)
    }) {
        val poly = listOf(
            Offset(-w / 2, -h * 0.32f),
            Offset(w / 2, -h / 2),
            Offset(w / 2 * 0.92f, h / 2),
            Offset(-w / 2, h * 0.42f),
        )
        if (mood == Mood.STARTLED) drawLightGlow(w * 0.05f, 0f, h * 2.2f, 0.55f * 0.55f) // globalAlpha .55 와 glow 알파 .55
        val housing = roundedPolygon(poly, min(w, h) * 0.28f)
        drawPath(housing, Color(0xFF0F151D), style = Fill)
        drawPath(housing, rgba(232, 238, 246, 0.6f), style = Stroke(2f))

        clipPath(housing) {
            val px = w * 0.06f + look * side * w * 0.12f
            when {
                wink -> glowStroke(
                    arcPath(0f, h * 0.32f, w * 0.27f, Pi * 1.15f, Pi * 1.85f),
                    WinkCore, Color(0xFFFFB020), max(2.5f, h * 0.16f), 12f,
                )
                mood == Mood.HAPPY -> glowStroke(
                    arcPath(0f, h * 0.32f, w * 0.27f, Pi * 1.15f, Pi * 1.85f),
                    LightCore, LightGlow, max(2.5f, h * 0.17f), 12f,
                )
                mood == Mood.SQUASHED -> glowStroke(
                    Path().apply {
                        moveTo(w * 0.18f, -h * 0.26f); lineTo(-w * 0.14f, 0f); lineTo(w * 0.18f, h * 0.26f)
                    },
                    LightCore, LightGlow, max(2.5f, h * 0.16f), 12f,
                )
                mood == Mood.DIZZY -> {
                    val rot = t * 7f * side
                    val spiral = Path()
                    var a = 0f
                    var first = true
                    val end = Pi * 4.2f
                    while (a < end) {
                        val r = a / end * h * 0.4f
                        val xx = cos(a + rot) * r
                        val yy = sin(a + rot) * r
                        if (first) { spiral.moveTo(xx, yy); first = false } else spiral.lineTo(xx, yy)
                        a += 0.18f
                    }
                    glowStroke(spiral, LightCore, LightGlow, max(1.8f, h * 0.09f), 12f)
                }
                else -> { // 편안(AMBIENT 포함), 깜짝: 동그란 빛
                    val pr = if (mood == Mood.STARTLED) h * 0.4f else h * 0.3f
                    drawLightGlow(px, 0f, pr * 1.9f, if (mood == Mood.STARTLED) 1f else 0.85f)
                    drawCircle(Color.White, pr * 0.55f, Offset(px, 0f))
                    drawCircle(rgba(255, 255, 255, 0.18f), pr * 1.05f, Offset(px, 0f), style = Stroke(1f))
                    if (blink > 0f) {
                        drawRect(lid, Offset(-w, -h), Size(w * 2, h * 0.5f + h * blink * 1.05f))
                    }
                }
            }
        }

        // LED 눈썹: 감정별 각도
        var ia = -h * 0.78f
        var oa = -h * 0.95f
        var cyb = -h * 1.08f
        var jit = 0f
        when (mood) {
            Mood.HAPPY -> { ia = -h * 0.98f; oa = -h * 1.12f; cyb = -h * 1.35f }
            Mood.SQUASHED -> { ia = -h * 0.58f; oa = -h * 0.98f; cyb = -h * 0.82f }
            Mood.STARTLED -> { ia = -h * 1.15f; oa = -h * 1.25f; cyb = -h * 1.5f }
            Mood.DIZZY -> jit = sin(t * 9f) * h * 0.12f
            else -> Unit
        }
        val brow = Path().apply {
            moveTo(-w * 0.42f, ia + jit)
            quadraticTo(w * 0.02f, cyb - jit, w * 0.46f, oa + jit)
        }
        glowStroke(
            brow,
            if (signal) Color(0xFFFFB020) else BrowCore,
            if (signal) Color(0xFFFF9A00) else LightGlow,
            max(2.2f, h * (if (signal) 0.14f else 0.11f)),
            10f,
        )
    }
}

/** 시안 `mouth`: 감정별 입. [g] 는 급가속의 기울임 정도(0.3 초과면 이를 앙다문 입). */
private fun DrawScope.drawMouth(x: Float, y: Float, mw: Float, h: Float, mood: Mood, t: Float, g: Float) {
    val line = rgba(8, 12, 18, 0.72f)
    val fill = rgba(18, 10, 14, 0.82f)
    val lw = max(2.4f, h * 0.12f)
    val stroke = Stroke(lw, cap = StrokeCap.Round, join = StrokeJoin.Round)
    withTransform({ translate(x, y) }) {
        if (g > 0.3f) {
            val gw = mw * 1.15f
            val gh = h * 0.3f
            drawRoundRect(fill, Offset(-gw / 2, -gh / 2), Size(gw, gh), CornerRadius(gh / 2))
            drawLine(rgba(255, 255, 255, 0.75f), Offset(-gw * 0.38f, 0f), Offset(gw * 0.38f, 0f), strokeWidth = 1.5f, cap = StrokeCap.Round)
            return@withTransform
        }
        fun wave(amp: Float) {
            val p = Path()
            for (i in 0..12) {
                val xx = -mw / 2 + mw * i / 12
                val yy = sin(i / 12f * Pi * 3 + t * (if (mood == Mood.DIZZY) 8f else 0f)) * amp
                if (i == 0) p.moveTo(xx, yy) else p.lineTo(xx, yy)
            }
            drawPath(p, line, style = stroke)
        }
        when (mood) {
            Mood.HAPPY -> {
                val p = Path().apply {
                    moveTo(-mw * 0.5f, 0f); quadraticTo(0f, h * 1.2f, mw * 0.5f, 0f); close()
                }
                drawPath(p, fill)
                drawOval(rgba(255, 120, 150, 0.85f), Offset(-mw * 0.2f, h * 0.42f - h * 0.14f), Size(mw * 0.4f, h * 0.28f))
            }
            Mood.SQUASHED -> wave(h * 0.12f)
            Mood.DIZZY -> wave(h * 0.16f)
            Mood.STARTLED ->
                drawOval(fill, Offset(-mw * 0.13f, h * 0.2f - h * 0.22f), Size(mw * 0.26f, h * 0.44f))
            else -> { // 편안(AMBIENT 포함): 작은 미소
                val p = Path().apply { moveTo(-mw * 0.36f, 0f); quadraticTo(0f, h * 0.42f, mw * 0.36f, 0f) }
                drawPath(p, line, style = stroke)
            }
        }
    }
}
