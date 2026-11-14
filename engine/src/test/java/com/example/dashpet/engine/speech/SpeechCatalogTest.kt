package com.example.dashpet.engine.speech

import com.example.dashpet.engine.EventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechCatalogTest {
    private val catalog = SpeechCatalog()
    private val config = SpeechConfig()

    @Test fun everyEventHasTwoDistinctVariants() {
        EventType.entries.forEach { type ->
            val v = catalog.variants(type)
            assertEquals("$type", 2, v.size)
            assertNotEquals("$type", v[0].text, v[1].text)
            assertNotEquals("$type", v[0].shortText, v[1].shortText)
            type.speechPriority() // 모든 이벤트에 우선순위가 있다
        }
    }

    @Test fun textsRespectDisplayLimits() {
        EventType.entries.flatMap { catalog.variants(it) }.forEach { line ->
            val t = line.text
            assertTrue(t, t.length <= SpeechLimits.MAX_CHARS)
            assertTrue(t, SpeechLimits.wordCount(t) <= SpeechLimits.MAX_WORDS)
            val rows = t.split("\n")
            assertTrue(t, rows.size <= SpeechLimits.MAX_LINES)
            assertTrue(t, rows.all { it.length <= SpeechLimits.MAX_LINE_CHARS })
        }
    }

    @Test fun shortTextsAreShortSingleSentences() {
        EventType.entries.flatMap { catalog.variants(it) }.forEach { line ->
            val s = line.shortText
            assertTrue(s, s.isNotBlank())
            assertTrue(s, s.length <= config.restrictedMaxChars)
            assertTrue(s, s.length <= line.text.length)
            assertEquals("한 문장이어야 한다: $s", 1, s.count { it == '.' || it == '?' || it == '!' })
        }
    }

    @Test fun noPlaceholdersOrDigits() {
        EventType.entries.flatMap { catalog.variants(it) }.forEach { line ->
            listOf(line.text, line.shortText).forEach { s ->
                assertTrue(s, s.none { it == '%' || it == '{' || it == '}' || it.isDigit() })
            }
        }
    }

    @Test fun pickDiffersFromLastAndIsDeterministic() {
        EventType.entries.forEach { type ->
            assertEquals(0, catalog.pick(type, null))
            assertEquals(1, catalog.pick(type, 0))
            assertEquals(0, catalog.pick(type, 1))
            assertEquals(catalog.pick(type, 0), catalog.pick(type, 0))
        }
    }

    @Test fun priorityTable() {
        assertEquals(SpeechPriority.DANGER, EventType.COLLISION_RISK.speechPriority())
        assertEquals(SpeechPriority.DANGER, EventType.LANE_DEPARTURE.speechPriority())
        assertEquals(SpeechPriority.WARN, EventType.HARD_BRAKE.speechPriority())
        assertEquals(SpeechPriority.PRAISE, EventType.GOOD_LANE_CHANGE.speechPriority())
        assertEquals(SpeechPriority.INFO, EventType.TRIP_END.speechPriority())
    }

    @Test(expected = IllegalArgumentException::class) fun preSilenceOutOfRangeRejected() {
        SpeechConfig(preSilenceMs = 100)
    }
}
