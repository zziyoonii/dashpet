package com.example.dashpet.pet

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val SparkColor = Color(0xFFFFE7A3)
private val SparkGlow = Color(0xFFFFD27A)
private val GlassDropDefault = Color(0xFF7C95C9)

/**
 * 시안 `drawParticles` 의 그리기(이동과 수명은 [Particles.update] 가 한다). 이번 단계는 반짝임과 유리 물방울만 그린다.
 * `shadowBlur 10` 글로우는 별 뒤에 방사형 빛 하나를 깔아 근사한다(R7).
 */
internal fun DrawScope.drawParticles(items: List<Particle>) {
    for (p in items) {
        val a = min(1f, p.life / p.max * 1.6f)
        when (p.type) {
            ParticleType.SPARK -> {
                val glow = Brush.radialGradient(
                    0f to SparkGlow.copy(alpha = 0.5f * a),
                    1f to SparkGlow.copy(alpha = 0f),
                    center = Offset(p.x, p.y),
                    radius = p.size * 2.6f,
                )
                drawCircle(glow, p.size * 2.6f, Offset(p.x, p.y))
                drawPath(starPath(p.x, p.y, p.size), SparkColor.copy(alpha = a))
            }
            ParticleType.GLASS_DROP -> {
                val col = (p.color ?: GlassDropDefault).copy(alpha = a * 0.85f)
                drawOval(col, Offset(p.x - p.size * 0.8f, p.y - p.size), Size(p.size * 1.6f, p.size * 2f))
                drawCircle(rgba(255, 255, 255, 0.6f * a * 0.85f), p.size * 0.25f, Offset(p.x - p.size * 0.25f, p.y - p.size * 0.35f))
            }
            else -> Unit // SWEAT, RAIN, Z, PUFF 는 배경 감정(US7)
        }
    }
}

/** 시안 `star(x, y, s)`: 꼭짓점 8개(바깥 s, 안쪽 0.38 s). */
private fun starPath(x: Float, y: Float, s: Float): Path = Path().apply {
    for (i in 0 until 8) {
        val a = i * PI.toFloat() / 4f
        val r = if (i % 2 == 1) s * 0.38f else s
        val px = x + cos(a) * r
        val py = y + sin(a) * r
        if (i == 0) moveTo(px, py) else lineTo(px, py)
    }
    close()
}

/**
 * 시안 `drawGlass`: 급제동 뒤 유리에 남는 입김 자국과, 유리에 눌린 동안 비치는 반사광 두 줄.
 * 자국이 사라지는 시점은 물리가 정한다([Smudge] 는 1.8초 뒤 null).
 */
internal fun DrawScope.drawGlass(ph: PetPhysicsState, w: Float, h: Float, smudgeDurationSec: Float) {
    ph.smudge?.let { s ->
        val k = (ph.clockSec - s.startSec) / smudgeDurationSec
        if (k in 0f..1f) {
            val alpha = 0.3f * (1f - k)
            val brush = Brush.radialGradient(
                0f to rgba(225, 235, 248, 0.9f * alpha),
                1f to rgba(225, 235, 248, 0f),
                center = Offset(s.x, s.y),
                radius = s.rx,
            )
            drawOval(brush, Offset(s.x - s.rx, s.y - s.ry), Size(s.rx * 2, s.ry * 2))
        }
    }
    val gp = ph.press
    if (gp > 0.05f) {
        val c = rgba(255, 255, 255, 0.07f * gp * 0.55f)
        drawPath(Path().apply {
            moveTo(w * 0.12f, 0f); lineTo(w * 0.26f, 0f); lineTo(w * 0.08f, h); lineTo(-w * 0.06f, h); close()
        }, c)
        drawPath(Path().apply {
            moveTo(w * 0.32f, 0f); lineTo(w * 0.36f, 0f); lineTo(w * 0.18f, h); lineTo(w * 0.14f, h); close()
        }, c)
    }
}
