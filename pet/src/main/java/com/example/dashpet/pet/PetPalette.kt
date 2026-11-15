package com.example.dashpet.pet

import androidx.compose.ui.graphics.Color
import kotlin.math.pow

/** 시안의 팔레트(`docs/DashPet 캐릭터 시안.html`). 값은 tasks.md T016 을 따른다. */
object PetPalette {
    val Bg = Color(0xFF0A0E14)
    val Panel = Color(0xFF111821)
    val Line = Color(0xFF233040)
    val Ink = Color(0xFFE6EBF1)
    val Muted = Color(0xFF8796A8)
    val Accent = Color(0xFFFFD27A)
    val Good = Color(0xFF3DDC84)
    val Warn = Color(0xFFF5A524)
    val Crit = Color(0xFFFF5A5A)

    /** 방향지시등 눈썹/눈 색(호박색). */
    val Amber = Color(0xFFFFB020)

    // 배경 그라데이션(시안 `drawBackground`): 기본, 비, 더위, 추위. 더위와 추위는 정차 중에만 바뀐다.
    val BgDefaultTop = Color(0xFF121A25)
    val BgDefaultBottom = Color(0xFF0B1017)
    val BgRainTop = Color(0xFF0E1726)
    val BgRainBottom = Color(0xFF090F18)
    val BgHotTop = Color(0xFF22171A)
    val BgHotBottom = Color(0xFF120D0F)
    val BgColdTop = Color(0xFF101C2A)
    val BgColdBottom = Color(0xFF0A121B)

    /** 이름 라벨 글자색. 위 배경 8가지 모두에서 대비 4.5:1 이상이어야 한다(FR-016, 테스트로 확인). */
    val NameLabel = Ink

    /** 시안 `mix(a, b, t)`: sRGB 성분을 선형 보간하고 정수로 반올림한다. */
    fun mix(a: Color, b: Color, t: Float): Color = mixColor(a, b, t)

    /** 시안 `lum`: `(0.299 R + 0.587 G + 0.114 B)` (성분은 0~1). */
    fun lum(c: Color): Float = luminance(c)

    /** 몸 윤곽선 알파: 어두운 몸(`lum < 0.3`)은 0.28, 그 밖에는 0.2. */
    fun outlineAlpha(body: Color): Float = if (lum(body) < 0.3f) 0.28f else 0.2f

    /** WCAG 2.x 대비율(1~21). 두 색의 순서에 상관없다. */
    fun contrastRatio(a: Color, b: Color): Float {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
    }

    private fun relativeLuminance(c: Color): Float {
        fun lin(x: Float): Float = if (x <= 0.03928f) x / 12.92f else ((x + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()
        return 0.2126f * lin(c.red) + 0.7152f * lin(c.green) + 0.0722f * lin(c.blue)
    }
}
