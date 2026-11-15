package com.example.dashpet.engine

import com.example.dashpet.signal.scenario.DemoScenarios
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoScenarioTest {
    private val expectedTypes = listOf(
        EventType.TRIP_START, EventType.SEATBELT_OK, EventType.HARD_CORNER, EventType.GOOD_LANE_CHANGE,
        EventType.LANE_DEPARTURE, EventType.HARD_BRAKE, EventType.TRIP_END,
    )

    private fun run() = runScenario(DemoScenarios.demoCommute)

    private fun finalScore(core: EngineCore, steps: List<EngineStep>) = 100 + steps.allEvents().sumOf { it.scoreDelta }

    @Test
    fun demoCommuteProducesSevenEventsInOrderAndScore94() { // SC-001
        val core = EngineCore()
        val steps = DemoScenarios.demoCommute.expand().map { core.process(it) }
        assertEquals(expectedTypes, steps.allEvents().map { it.type })
        val trip = core.completedTrips().single()
        assertEquals(94, trip.score)
        assertEquals(expectedTypes, trip.events.map { it.type })
        assertEquals(null, core.currentTrip())
    }

    @Test
    fun eventTimesMatchTheStory() {
        val events = run().allEvents().associateBy { it.type }
        assertEquals(8_000L, events.getValue(EventType.TRIP_START).atMs)
        assertEquals(10_000L, events.getValue(EventType.SEATBELT_OK).atMs)
        assertTrue(events.getValue(EventType.HARD_CORNER).atMs in 22_000L..23_000L)
        assertEquals(32_000L, events.getValue(EventType.GOOD_LANE_CHANGE).atMs)
        assertEquals(38_000L, events.getValue(EventType.LANE_DEPARTURE).atMs)
        assertTrue(events.getValue(EventType.HARD_BRAKE).atMs in 46_000L..48_000L)
        assertEquals(52_000L, events.getValue(EventType.TRIP_END).atMs)
    }

    @Test
    fun noHardAccelDuringGentleAcceleration() {
        val steps = run()
        assertTrue(steps.eventsOf(EventType.HARD_ACCEL).isEmpty())
        assertTrue(steps.filter { it.output.atMs in 12_000L..20_000L }.all { it.events.isEmpty() })
    }

    @Test
    fun hundredRunsAreIdentical() { // SC-002: 이벤트 순서, 감정 전환, 점수
        val first = run()
        repeat(99) { assertEquals("run ${it + 2}", first, run()) }
    }

    @Test
    fun moodTransitionsFollowTheStory() {
        val moods = run().map { it.output.mood }.fold(mutableListOf<Mood>()) { acc, m ->
            if (acc.lastOrNull() != m) acc += m
            acc
        }
        assertEquals(
            listOf(
                Mood.IDLE, Mood.HAPPY, Mood.IDLE, Mood.SQUASHED, Mood.IDLE, Mood.HAPPY,
                Mood.DIZZY, Mood.IDLE, Mood.SQUASHED, Mood.IDLE, Mood.HAPPY,
            ), // 기쁨(32초)은 38초 신호에서 만료되며 같은 단계에서 어지러움이 시작되므로 IDLE 이 보이지 않는다
            moods,
        )
    }

    @Test
    fun noFullMotionWhileDriving() {
        // 8초 출발 ~ 52초 도착 직전까지 제한이 걸려 있으므로 풀 모션은 없다.
        val driving = run().filter { it.output.atMs in 8_000L until 52_000L }
        assertTrue(driving.isNotEmpty())
        assertTrue(driving.none { it.output.tier == MotionTier.FULL })
        // 도착 후 제한이 풀리면 풀 모션
        assertEquals(MotionTier.FULL, run().last().output.tier)
    }

    @Test
    fun hardCornerLeansLeftOnRightTurn() {
        // research.md R5: 우회전(방위각 증가) -> LEFT. [확인 필요] 방위 부호와 데모 영상의 기대 방향(미결 항목 1).
        assertEquals(Direction4.LEFT, run().eventsOf(EventType.HARD_CORNER).single().direction)
        assertEquals(Direction4.FRONT, run().eventsOf(EventType.HARD_BRAKE).single().direction)
    }

    @Test
    fun scoreIsExactly94() {
        val core = EngineCore()
        val steps = DemoScenarios.demoCommute.expand().map { core.process(it) }
        assertEquals(94, finalScore(core, steps))
    }
}
