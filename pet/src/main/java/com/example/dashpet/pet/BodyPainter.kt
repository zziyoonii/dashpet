package com.example.dashpet.pet

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * 시안 `bodyPath`: 소프트바디 22개 점을 중점 이차 곡선으로 잇는 몸 경로. 유리에 눌리면([PetGeometry.e])
 * 원형을 잃고 물처럼 번진 가장자리가 된다. 시계 [t] 는 컨트롤러의 물리 시계다.
 */
internal fun petBodyPath(g: PetGeometry, ph: PetPhysicsState, w: Float, h: Float, panel: Float, t: Float, reduced: Boolean): Path {
    val body = ph.softBody
    val n = body.n
    val e = g.e
    val m = 4f + (1f - e) * (panel - 4f)
    val cl = m
    val cr = w - m
    val flat = 0.35f
    val ph4 = ph.splatPhases
    val xs = FloatArray(n)
    val ys = FloatArray(n)
    for (i in 0 until n) {
        val a = body.angles[i]
        val s = sin(a)
        val c = cos(a)
        // 유리에 눌리면 원형을 잃고 물처럼 번진 불규칙한 가장자리가 된다
        val ooze = if (e > 0f) {
            e * (0.085f * sin(2 * a + ph4[0]) + 0.06f * sin(3 * a + ph4[1]) +
                0.04f * sin(5 * a + ph4[2] + t * 1.4f) + 0.025f * sin(7 * a + ph4[3] - t * 2f))
        } else 0f
        val k = (1f + body.d[i] * (1f - 0.6f * e)) * (1f + ooze) *
            (1f + (if (reduced) 0f else 0.01f * sin(t * 3f + i * 1.7f)))
        var yf = s * g.ry
        if (s > flat) yf = flat * g.ry + (s - flat) * g.ry * 0.55f
        if (s < 0f) yf *= 1f - 0.2f * g.lean
        val y = yf + (s * g.ry - yf) * e
        val x = c * g.rx * (1f + 0.13f * max(0f, s) * (1f - e))
        xs[i] = min(cr, max(cl, g.cx + x * k))
        ys[i] = min(h - 4f, max(4f, g.cy + y * k))
    }
    val path = Path()
    fun mx(i: Int, j: Int) = (xs[i] + xs[j]) / 2f
    fun my(i: Int, j: Int) = (ys[i] + ys[j]) / 2f
    path.moveTo(mx(n - 1, 0), my(n - 1, 0))
    for (i in 0 until n) {
        val j = (i + 1) % n
        path.quadraticTo(xs[i], ys[i], mx(i, j), my(i, j))
    }
    path.close()
    return path
}

/** 더위는 붉은 기(#ff7a59 0.28), 추위는 푸른 기(#9cc8ff 0.3)가 몸 색에 섞인다. 그 밖에는 몸 색 그대로. */
internal fun ambientBodyColor(base: Color, ambient: AmbientMood?): Color = when (ambient) {
    AmbientMood.HOT -> mixColor(base, Color(0xFFFF7A59), 0.28f)
    AmbientMood.COLD -> mixColor(base, Color(0xFF9CC8FF), 0.3f)
    else -> base
}

/**
 * 몸의 바닥 쪽 그리기(시안 `drawPet` 의 몸 부분): 속도선, 눌린 자국, 그림자, 그라데이션 몸, 윤곽선, 광택.
 * 얼굴은 이 위에, 유리 번짐 덮개([drawBodyGlassOverlay])는 얼굴 위에 그린다.
 */
internal fun DrawScope.drawBodyBase(
    base: Color,
    g: PetGeometry,
    ph: PetPhysicsState,
    body: Path,
    t: Float,
    reduced: Boolean,
    w: Float,
    h: Float,
    ambient: AmbientMood? = null,
) {
    val e = g.e
    // 아래쪽 어두운 끝은 원래 몸 색 기준이다.
    val bodyCol = ambientBodyColor(base, ambient)
    drawSpeedLines(g.cx, g.cy, g.lean, t, reduced, w, h)
    drawDent(g.cx, g.cy, g.rx, g.ry, g.lean)

    // 바닥 그림자
    val sh = max(0.3f, 1f - ph.oy / 220f) * (1f - e)
    if (sh > 0.02f) {
        val rx = g.groundRx * 1.05f * sh
        val ry = g.r * 0.1f * sh
        drawOval(rgba(0, 0, 0, 0.38f * sh), Offset(g.groundX - rx, g.groundY + 3f - ry), Size(rx * 2, ry * 2))
    }

    // 몸: 위가 밝고 아래가 어두운 그라데이션과 윤곽선
    val brush = Brush.verticalGradient(
        0f to mixColor(bodyCol, Color.White, 0.32f - 0.12f * e),
        0.55f to bodyCol,
        1f to mixColor(base, Color.Black, 0.3f - 0.1f * e),
        startY = g.cy - g.ry,
        endY = g.cy + g.ry * 0.75f,
    )
    drawPath(body, brush)
    drawPath(body, rgba(255, 255, 255, PetPalette.outlineAlpha(base)), style = Stroke(2f))

    // 광택(몸 안쪽만)
    val hiAlpha = 1f - e * 0.7f
    clipPath(body) {
        rotate(-0.5f.toDegrees(), Offset(g.cx - g.rx * 0.42f, g.cy - g.ry * 0.55f)) {
            drawOval(
                rgba(255, 255, 255, 0.28f * hiAlpha),
                Offset(g.cx - g.rx * 0.42f - g.rx * 0.26f, g.cy - g.ry * 0.55f - g.ry * 0.13f),
                Size(g.rx * 0.52f, g.ry * 0.26f),
            )
        }
        rotate(-0.4f.toDegrees(), Offset(g.cx - g.rx * 0.62f, g.cy - g.ry * 0.28f)) {
            drawOval(
                rgba(255, 255, 255, 0.14f * hiAlpha),
                Offset(g.cx - g.rx * 0.62f - g.rx * 0.07f, g.cy - g.ry * 0.28f - g.ry * 0.06f),
                Size(g.rx * 0.14f, g.ry * 0.12f),
            )
        }
        // 추위의 서리: 몸 윗부분에 옅은 푸른 띠
        if (ambient == AmbientMood.COLD) {
            drawRect(rgba(210, 235, 255, 0.18f), Offset(g.cx - g.rx * 1.2f, g.cy - g.ry * 1.1f), Size(g.rx * 2.4f, g.ry * 0.55f))
        }
    }
}

/**
 * 배경 감정의 몸 덧그림(벽 접촉 빛 다음, 얼굴 앞). 더위는 바닥으로 녹아내린 방울 세 개(눌림이 적을 때만),
 * 비는 노란 우비(눌림이 적을 때만)다. 시계 [t] 는 컨트롤러의 물리 시계다.
 */
internal fun DrawScope.drawAmbientBodyExtras(base: Color, g: PetGeometry, t: Float, ambient: AmbientMood?) {
    val e = g.e
    if (ambient == AmbientMood.HOT && e < 0.3f) {
        val bodyCol = ambientBodyColor(base, AmbientMood.HOT)
        val drip = mixColor(bodyCol, Color.Black, 0.18f)
        val by = g.groundY - 1f
        listOf(-0.55f, -0.1f, 0.42f).forEachIndexed { i, o ->
            val len = 6f + 4f * sin(t * 2f + i * 2f)
            val rx = 5f
            val ry = len * 0.6f + 3f
            drawOval(drip, Offset(g.cx + g.rx * o - rx, by + len * 0.3f - ry), Size(rx * 2, ry * 2))
        }
    }
    if (ambient == AmbientMood.RAIN && e < 0.4f) drawHood(g.cx, g.cy, g.rx, g.ry, flat = false)
}

private fun Float.toDegrees(): Float = (this * 180.0 / PI).toFloat()

/** 시안 `drawDent`: 급가속 때 뒤로 밀려 배경에 푹 눌린 자국. */
private fun DrawScope.drawDent(pcx: Float, pcy: Float, prx: Float, pry: Float, lean: Float) {
    if (lean < 0.02f) return
    // 시안의 radialGradient(r0 = prx*.3, r1 = prx*1.8): 안쪽 반지름까지는 같은 색이므로 0.3/1.8 지점에서 시작한다.
    val brush = Brush.radialGradient(
        0f to rgba(0, 0, 0, 0.55f * lean),
        (0.3f / 1.8f) to rgba(0, 0, 0, 0.55f * lean),
        1f to rgba(0, 0, 0, 0f),
        center = Offset(pcx, pcy),
        radius = prx * 1.8f,
    )
    drawOval(brush, Offset(pcx - prx * 1.8f, pcy - pry * 1.7f), Size(prx * 3.6f, pry * 3.4f))
    val ring = rgba(255, 255, 255, 0.13f * lean)
    for (k in 1..3) {
        val rx = prx * (1.1f + k * 0.22f)
        val ry = pry * (1.05f + k * 0.22f)
        drawArc(
            ring,
            startAngle = (PI * 1.08).toFloat().toDegrees(),
            sweepAngle = (PI * (1.92 - 1.08)).toFloat().toDegrees(),
            useCenter = false,
            topLeft = Offset(pcx - rx, pcy + pry * 0.1f - ry),
            size = Size(rx * 2, ry * 2),
            style = Stroke(1.5f),
        )
    }
}

/** 시안 `drawSpeedLines`: 급가속 때 화면 가장자리에서 펫 쪽으로 빨려 드는 선. 모션 줄이기에서는 그리지 않는다. */
private fun DrawScope.drawSpeedLines(cx: Float, cy: Float, lean: Float, t: Float, reduced: Boolean, w: Float, h: Float) {
    if (lean < 0.05f || reduced) return
    val color = rgba(200, 215, 235, 0.4f * lean * 0.65f)
    val n = 20
    val mm = max(w, h)
    for (i in 0 until n) {
        val a = i.toFloat() / n * (PI * 2).toFloat() + i * 0.37f
        val ph = (t * 1.8f + i * 0.29f) % 1f
        val r1 = mm * (0.78f - ph * 0.42f)
        val r2 = r1 - mm * 0.13f
        drawLine(
            color,
            Offset(cx + cos(a) * r1, cy + sin(a) * r1),
            Offset(cx + cos(a) * r2, cy + sin(a) * r2),
            strokeWidth = 1.6f,
            cap = StrokeCap.Round,
        )
    }
}

/**
 * 유리에 눌린 번짐 덮개(시안 `drawPet` 끝의 `e > 0.03` 블록): 몸 안쪽에 옅은 빛과 두꺼운 밝은 테두리.
 * 시안의 `globalAlpha = e` 는 각 색의 알파에 곱해 같게 만든다.
 */
internal fun DrawScope.drawBodyGlassOverlay(g: PetGeometry, body: Path, w: Float, h: Float) {
    val e = g.e
    if (e <= 0.03f) return
    clipPath(body) {
        val brush = Brush.radialGradient(
            0f to rgba(255, 255, 255, 0.2f * e),
            0.6f to rgba(255, 255, 255, 0.05f * e),
            1f to rgba(255, 255, 255, 0f),
            center = Offset(g.cx - g.rx * 0.15f, g.cy - g.ry * 0.2f),
            radius = max(g.rx, g.ry),
        )
        drawRect(brush, size = Size(w, h))
        drawPath(body, rgba(255, 255, 255, 0.38f * e), style = Stroke(10f))
        drawPath(body, rgba(0, 0, 0, 0.18f * e), style = Stroke(3f))
    }
}
