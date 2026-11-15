package com.example.dashpet.pet

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform

/**
 * 펫 한 장을 그린다. 순서는 시안과 같다: 배경, 몸, 벽 접촉 빛, 얼굴, 유리 번짐 덮개, 파티클, 유리 반사광.
 * 이 함수는 [state] 를 바꾸지 않고 난수와 시계를 읽지 않는다(컨트롤러가 정한 값만 그린다).
 *
 * 좌표는 시안의 CSS px 와 같은 논리 단위라서 밀도 배율을 한 번 적용하고 그린다.
 * 정적 모드([PetRenderState.physics] 가 null)는 [drawStaticPet] 이 그린다(움직임 없는 별도 모양).
 * 표시 영역이 0 이하이면 아무것도 그리지 않는다.
 */
fun DrawScope.drawPet(state: PetRenderState) {
    val w = state.viewportWidth
    val h = state.viewportHeight
    if (!(w > 0f && h > 0f)) return
    val config = state.config
    val ph = state.physics ?: return drawStaticPet(state)
    val t = ph.clockSec
    val panel = config.panelWidth
    val base = state.appearance.bodyColor
    val dpScale = density
    withTransform({ scale(dpScale, dpScale, Offset.Zero) }) {
        val g = petGeometry(ph, w, h, config)
        drawPetBackground(w, h, g.groundY, panel, state.ambient)
        val body = petBodyPath(g, ph, w, h, panel, t, state.reducedMotion)
        drawBodyBase(base, g, ph, body, t, state.reducedMotion, w, h, state.ambient)
        drawContactGlow(w, panel, ph.pen, ph.ox, g.cy, g.ry)
        drawAmbientBodyExtras(base, g, t, state.ambient)
        drawFace(state.mood, base, state.appearance.personality, g, ph, t, state.turnSignal, state.wink, state.ambient)
        drawBodyGlassOverlay(g, body, w, h)
        drawParticles(state.particles)
        drawGlass(ph, w, h, config.smudgeDurationSec)
    }
}
