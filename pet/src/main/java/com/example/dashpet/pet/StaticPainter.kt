package com.example.dashpet.pet

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.example.dashpet.signal.Side
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min

private val Pi = PI.toFloat()
private val HoodFlat = Color(0xFFC99A1E)
private val HoodBright = Color(0xFFFFCC33)
private val EyeHousing = rgba(8, 12, 18, 0.55f)
private val ClosedEyeLine = rgba(8, 12, 18, 0.65f)
private val BodyOutline = rgba(255, 255, 255, 0.18f)

/**
 * 정적 등급을 그린다(시안 `drawPet` 의 `moving` 분기에서 움직임을 모두 뺀 모양). 시간, 난수, 물리를 읽지 않는다.
 *
 * 몸은 단색 + 윤곽선(`rgba(255,255,255,.18)`, 두께 2), 눈은 어두운 하우징 다각형(동공 없음), 입 없음, 감은 눈은 어두운 호,
 * 방향지시등 눈썹은 호박색 선(정적), 비는 우비 실루엣(`hood(flat = true)`)이다.
 * 소프트바디 변위, 숨쉬기 흔들림, 번짐, 파티클, 속도선, 눌린 자국, 벽 접촉 빛은 그리지 않는다.
 * 좌표는 논리 단위이고 밀도 배율을 한 번 적용한다. 표시 영역이 0 이하이면 아무것도 그리지 않는다.
 */
fun DrawScope.drawStaticPet(state: PetRenderState) {
    val w = state.viewportWidth
    val h = state.viewportHeight
    val pose = state.staticPose ?: return
    if (!(w > 0f && h > 0f)) return
    val config = state.config
    val dpScale = density
    withTransform({ scale(dpScale, dpScale, Offset.Zero) }) {
        val groundY = h * 0.8f
        // 비(우비)일 때는 배경도 어두운 푸른색이다(시안 `drawBackground` 는 정적에서도 비를 반영한다).
        drawPetBackground(w, h, groundY, config.panelWidth, if (pose.hood) AmbientMood.RAIN else null)

        // 몸 기하: 시안 moving 분기(R = min(W,H) * 0.17, 깊이 1, 눌림 0, 기울임 0)
        val r = min(w, h) * config.staticRadiusFactor
        val rx = r * pose.scaleX * 1.05f
        val ry = r * pose.scaleY * 0.92f
        val bottom = 0.35f + 0.65f * 0.55f
        val cx = w / 2f
        val cy = groundY - bottom * ry

        // 바닥 그림자: oy = 0, 압력 0 이므로 크기 배율 1 (시안 `sh = max(.3, 1 - oy/220) * (1 - e)`)
        drawOval(rgba(0, 0, 0, 0.38f), Offset(cx - rx * 1.05f, groundY + 3f - r * 0.1f), Size(rx * 2.1f, r * 0.2f))

        // 몸과 얼굴은 몸 아래 중앙을 축으로 함께 기운다.
        rotate(pose.tiltDeg, Offset(cx, groundY)) {
            val geometry = PetGeometry(cx, cy, rx, ry, r, r, 0f, 0f, cx, rx, groundY)
            val still = PetPhysicsState(SoftBody(config)) // 변위 0
            val body = petBodyPath(geometry, still, w, h, config.panelWidth, 0f, reduced = true) // 숨쉬기 흔들림 없음
            drawPath(body, pose.bodyColor, style = Fill)
            drawPath(body, BodyOutline, style = Stroke(2f))
            if (pose.hood) drawHood(cx, cy, rx, ry, flat = true)
            drawStaticEyes(pose, cx, cy, rx, ry, r)
        }
    }
}

/**
 * 시안 `hood(cx, cy, rx, ry, flat)`: 위쪽 반달 모양 우비. [flat] 은 정적용(어두운 단색), 아니면 풀 모션용(밝은 노랑, 윤곽선, 꼭대기 단추).
 */
internal fun DrawScope.drawHood(cx: Float, cy: Float, rx: Float, ry: Float, flat: Boolean) {
    val oval = Rect(cx - rx * 1.08f, cy - ry * 0.05f - ry * 1.08f, cx + rx * 1.08f, cy - ry * 0.05f + ry * 1.08f)
    val path = Path().apply {
        arcTo(oval, 1.04f * 180f, 0.92f * 180f, true)
        lineTo(cx + rx * 1.12f, cy - ry * 0.12f)
        quadraticTo(cx, cy - ry * 0.42f, cx - rx * 1.12f, cy - ry * 0.12f)
        close()
    }
    drawPath(path, if (flat) HoodFlat else HoodBright, style = Fill)
    if (!flat) {
        drawPath(path, rgba(120, 80, 0, 0.35f), style = Stroke(2f))
        drawCircle(HoodBright, ry * 0.07f, Offset(cx, cy - ry * 1.12f))
    }
}

/** 시안 moving 분기의 눈: `ew = R*.42`, `eh = R*.26`, `ey = cy - ry*.18`, `ex = rx*.4`. 눈 개별 그리기는 좌우 대칭이다. */
private fun DrawScope.drawStaticEyes(pose: StaticPose, cx: Float, cy: Float, rx: Float, ry: Float, r: Float) {
    val ew = r * 0.42f
    val eh = r * 0.26f
    val ey = cy - ry * 0.18f
    val ex = rx * 0.4f
    for (side in Side.entries) {
        val sd = if (side == Side.LEFT) -1f else 1f
        withTransform({
            translate(cx + sd * ex, ey)
            scale(sd, 1f, Offset.Zero)
        }) {
            if (pose.closedEye == side) {
                val arc = arcPath(0f, eh * 0.32f, ew * 0.3f, Pi * 1.15f, Pi * 1.85f)
                drawPath(arc, ClosedEyeLine, style = Stroke(max(2.5f, eh * 0.24f), cap = StrokeCap.Round))
            } else {
                val poly = listOf(
                    Offset(-ew / 2, -eh * 0.32f),
                    Offset(ew / 2, -eh / 2),
                    Offset(ew / 2 * 0.92f, eh / 2),
                    Offset(-ew / 2, eh * 0.42f),
                )
                drawPath(roundedPolygon(poly, eh * 0.3f), EyeHousing, style = Fill)
            }
            if (pose.amberBrow == side) {
                val brow = Path().apply {
                    moveTo(-ew * 0.4f, -eh * 0.85f)
                    quadraticTo(0f, -eh * 1.1f, ew * 0.45f, -eh * 0.95f)
                }
                drawPath(brow, PetPalette.Amber, style = Stroke(max(2.5f, eh * 0.16f), cap = StrokeCap.Round))
            }
        }
    }
}
