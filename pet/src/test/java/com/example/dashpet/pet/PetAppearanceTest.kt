package com.example.dashpet.pet

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/** FR-011, FR-016, FR-021. */
class PetAppearanceTest {
    @Test
    fun defaultsAreUsedWhenNoValueIsGiven() {
        val a = PetAppearance()
        assertEquals("내 차", a.name)
        assertEquals(Color(0xFF4F7CFF), a.bodyColor)
        assertEquals(Personality.STURDY, a.personality)
    }

    @Test
    fun emptyOrBlankNameFallsBackToDefaultDisplayName() {
        assertEquals("내 차", PetAppearance(name = "").displayName)
        assertEquals("내 차", PetAppearance(name = "   ").displayName)
        assertEquals("둥둥이", PetAppearance(name = "둥둥이").displayName)
    }

    @Test
    fun anyInputBuildsWithoutThrowing() {
        val weird = listOf("", " ", "\n\t", "가".repeat(10_000), "😀🚗", "\u0000")
        weird.forEach { n ->
            Personality.entries.forEach { p ->
                listOf(Color.Transparent, Color.Black, Color.Unspecified, Color(0xFFFFFFFF)).forEach { c ->
                    assertNotNull(PetAppearance(n, c, p).displayName)
                }
            }
        }
    }

    @Test
    fun enumsHaveTheSpecifiedValues() {
        assertEquals(listOf("STURDY", "LIVELY", "CUTE"), Personality.entries.map { it.name })
        assertEquals(listOf("HOT", "COLD", "RAIN", "QUIET"), AmbientMood.entries.map { it.name })
    }
}
