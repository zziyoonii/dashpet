package com.example.dashpet.pet

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.min
import kotlin.math.sign

private val BgTop = Color(0xFF121A25)
private val BgBottom = Color(0xFF0B1017)

/**
 * 시안 `drawBackground`(홈 화면, 배경 감정 없음): 위아래 그라데이션, 32px 격자선, 바닥 빛, 바닥선, 양쪽 패널(벽).
 * 좌표는 논리 단위이고 호출하는 쪽이 밀도 배율을 적용해 둔다.
 */
internal fun DrawScope.drawPetBackground(w: Float, h: Float, groundY: Float, panel: Float) {
    drawRect(Brush.verticalGradient(listOf(BgTop, BgBottom), startY = 0f, endY = h), size = Size(w, h))
    // 격자선: 1px, x + .5 위치, 바닥선까지
    val gridColor = rgba(255, 255, 255, 0.035f)
    var x = 0f
    while (x < w) {
        drawLine(gridColor, Offset(x + .5f, 0f), Offset(x + .5f, groundY), strokeWidth = 1f)
        x += 32f
    }
    drawRect(
        Brush.verticalGradient(listOf(rgba(255, 210, 122, 0.06f), rgba(0, 0, 0, 0f)), startY = groundY, endY = h),
        topLeft = Offset(0f, groundY),
        size = Size(w, h - groundY),
    )
    drawLine(rgba(255, 255, 255, 0.12f), Offset(0f, groundY + .5f), Offset(w, groundY + .5f), strokeWidth = 1f)
    val wall = rgba(255, 255, 255, 0.06f)
    drawRoundRect(wall, Offset(2f, 48f), Size(panel - 5f, groundY - 52f), CornerRadius(4f))
    drawRoundRect(wall, Offset(w - panel + 3f, 48f), Size(panel - 5f, groundY - 52f), CornerRadius(4f))
}

/**
 * 시안 `contactGlow`: 벽에 파고들 때([pen] 2 이상) 닿은 쪽 벽에 번지는 노란 빛.
 * `shadowBlur 14` 는 같은 모양을 넓고 옅게 한 번 더 그려 근사한다(R7).
 */
internal fun DrawScope.drawContactGlow(w: Float, panel: Float, pen: Float, ox: Float, cy: Float, ry: Float) {
    if (pen < 2f) return
    val side = sign(ox)
    val x = if (side > 0) w - panel + 1f else 2f
    val a = min(0.85f, pen / 40f)
    val top = cy - ry * 1.1f
    val bottom = cy + ry * 0.8f
    fun gradient(alpha: Float) = Brush.verticalGradient(
        0f to rgba(255, 210, 122, 0f),
        0.5f to rgba(255, 210, 122, alpha),
        1f to rgba(255, 210, 122, 0f),
        startY = top,
        endY = bottom,
    )
    val glowPad = 6f
    drawRoundRect(gradient(a * 0.35f), Offset(x - glowPad, top - glowPad), Size(panel - 3f + glowPad * 2, ry * 1.9f + glowPad * 2), CornerRadius(4f + glowPad))
    drawRoundRect(gradient(a), Offset(x, top), Size(panel - 3f, ry * 1.9f), CornerRadius(4f))
}
