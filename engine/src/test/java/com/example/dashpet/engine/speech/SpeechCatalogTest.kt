package com.example.dashpet.engine.speech

import com.example.dashpet.engine.EventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 말투 3종(듬직, 활발, 애교) 모두에 같은 규칙을 적용한다(specs/005). */
class SpeechCatalogTest {
    private val catalogs = SpeechTone.entries.associateWith { SpeechCatalog.forTone(it) }
    private val config = SpeechConfig()

    private fun allLines() = catalogs.flatMap { (tone, c) -> EventType.entries.flatMap { t -> c.variants(t).map { Triple(tone, t, it) } } }

    @Test fun everyEventHasTwoDistinctVariantsInEveryTone() {
        catalogs.forEach { (tone, catalog) ->
            EventType.entries.forEach { type ->
                val v = catalog.variants(type)
                assertEquals("$tone $type", 2, v.size)
                assertNotEquals("$tone $type", v[0].text, v[1].text)
                assertNotEquals("$tone $type", v[0].shortText, v[1].shortText)
                type.speechPriority() // 모든 이벤트에 우선순위가 있다
            }
        }
    }

    @Test fun textsRespectDisplayLimits() {
        allLines().forEach { (tone, type, line) ->
            val t = line.text
            val tag = "$tone $type: $t"
            assertTrue(tag, t.length <= SpeechLimits.MAX_CHARS)
            assertTrue(tag, SpeechLimits.wordCount(t) <= SpeechLimits.MAX_WORDS)
            val rows = t.split("\n")
            assertTrue(tag, rows.size <= SpeechLimits.MAX_LINES)
            assertTrue(tag, rows.all { it.length <= SpeechLimits.MAX_LINE_CHARS })
        }
    }

    @Test fun shortTextsAreShortSingleSentences() {
        allLines().forEach { (tone, type, line) ->
            val s = line.shortText
            val tag = "$tone $type: $s"
            assertTrue(tag, s.isNotBlank())
            assertTrue(tag, s.length <= config.restrictedMaxChars)
            assertTrue(tag, s.length <= line.text.length)
            assertEquals("한 문장이어야 한다: $tag", 1, s.count { it == '.' || it == '?' || it == '!' })
        }
    }

    @Test fun noPlaceholdersOrDigits() {
        allLines().forEach { (tone, type, line) ->
            listOf(line.text, line.shortText).forEach { s ->
                assertTrue("$tone $type: $s", s.none { it == '%' || it == '{' || it == '}' || it.isDigit() })
            }
        }
    }

    @Test fun tonesAreDistinct() {
        EventType.entries.forEach { type ->
            val sets = SpeechTone.entries.map { tone -> catalogs.getValue(tone).variants(type).map { it.text }.toSet() }
            assertTrue("$type: 말투마다 문장이 달라야 한다", sets.flatten().size == sets.sumOf { it.size })
        }
    }

    @Test fun sturdyIsTheDefaultCatalog() {
        EventType.entries.forEach { type ->
            assertEquals(SpeechCatalog().variants(type), SpeechCatalog.forTone(SpeechTone.STURDY).variants(type))
        }
    }

    @Test fun pickDiffersFromLastAndIsDeterministic() {
        val catalog = SpeechCatalog()
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
