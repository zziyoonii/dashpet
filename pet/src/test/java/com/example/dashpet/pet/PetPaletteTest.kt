package com.example.dashpet.pet

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** T056. 색 혼합, 휘도, 윤곽선 알파 규칙, 대비율(WCAG)과 이름 라벨의 대비. */
class PetPaletteTest {
    @Test
    fun mixIsSrgbLinearAndRounded() {
        val m = PetPalette.mix(Color(0xFF000000), Color(0xFFFFFFFF), 0.5f)
        assertEquals(128f / 255f, m.red, 0f) // 127.5 → 128
        assertEquals(Color(0xFF4F7CFF), PetPalette.mix(Color(0xFF4F7CFF), Color(0xFF000000), 0f))
        assertEquals(Color(0xFF000000), PetPalette.mix(Color(0xFF4F7CFF), Color(0xFF000000), 1f))
    }

    @Test
    fun luminanceUsesRec601Weights() {
        assertEquals(0f, PetPalette.lum(Color.Black), 1e-6f)
        assertEquals(1f, PetPalette.lum(Color.White), 1e-6f)
        assertEquals(0.299f, PetPalette.lum(Color.Red), 1e-6f)
        assertEquals(0.587f, PetPalette.lum(Color.Green), 1e-6f)
        assertEquals(0.114f, PetPalette.lum(Color.Blue), 1e-6f)
    }

    @Test
    fun outlineAlphaBoundaryAt0_3() {
        // lum < 0.3 이면 0.28, 아니면 0.2
        assertEquals(0.28f, PetPalette.outlineAlpha(Color(0xFF444444)), 0f) // 0.267
        assertEquals(0.2f, PetPalette.outlineAlpha(Color(0xFF4D4D4D)), 0f)  // 0.302
        assertEquals(0.2f, PetPalette.outlineAlpha(Color.White), 0f)
    }

    @Test
    fun contrastRatioMatchesWcag() {
        assertEquals(21f, PetPalette.contrastRatio(Color.Black, Color.White), 0.01f)
        assertEquals(1f, PetPalette.contrastRatio(Color.White, Color.White), 0.001f)
        // 순서에 상관없다
        assertEquals(
            PetPalette.contrastRatio(Color.Black, Color.White),
            PetPalette.contrastRatio(Color.White, Color.Black),
            0f,
        )
    }

    @Test
    fun nameLabelContrastIsAtLeast4_5AgainstEveryBackground() {
        val backgrounds = listOf(
            PetPalette.BgDefaultTop, PetPalette.BgDefaultBottom,
            PetPalette.BgRainTop, PetPalette.BgRainBottom,
            PetPalette.BgHotTop, PetPalette.BgHotBottom,
            PetPalette.BgColdTop, PetPalette.BgColdBottom,
        )
        for (bg in backgrounds) {
            val ratio = PetPalette.contrastRatio(PetPalette.NameLabel, bg)
            assertTrue("대비 $ratio < 4.5 (배경 $bg)", ratio >= 4.5f)
        }
    }
}
