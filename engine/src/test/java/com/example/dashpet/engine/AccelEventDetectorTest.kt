package com.example.dashpet.engine

import com.example.dashpet.signal.scenario.scenario
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccelEventDetectorTest {
    private val raw = EngineConfig(smoothingAlpha = 1.0)

    private fun hardBrake(config: EngineConfig = EngineConfig(), pedal: Boolean = true) =
        scenario("brake") {
            at(0) { brakePedal(pedal) }
            at(0) { speedRamp(50, 0, 2_000) }
        }.expand().let { AccelEventDetector(config).feed(it) }

    private fun corner(deltaDeg: Int, config: EngineConfig = EngineConfig(), kmh: Double = 50.0) =
        scenario("corner") {
            at(0) { speed(kmh / 3.6); heading(90) }
            at(1_000) { headingTurn(deltaDeg, 1_000) }
        }.expand().let { AccelEventDetector(config).feed(it) }

    @Test
    fun hardBrakeFrom50To0In2SecondsWithPedal() {
        val events = hardBrake()
        assertEquals(1, events.size)
        val e = events.single()
        assertEquals(EventType.HARD_BRAKE, e.type)
        assertEquals(Direction4.FRONT, e.direction)
        assertTrue(e.intensity > 0f && e.intensity <= 1f)
        assertEquals(-2, e.scoreDelta)
    }

    @Test
    fun sameDecelerationWithoutPedalIsNotHardBrake() {
        assertTrue(hardBrake(pedal = false).isEmpty())
    }

    @Test
    fun rightTurnIsHardCornerLeaning_LEFT() {
        // 방위각 증가 = 우회전 -> 몸은 왼쪽으로 쏠림 (research.md R5). [확인 필요] 방위 부호
        val events = corner(+30)
        assertEquals(1, events.size)
        assertEquals(EventType.HARD_CORNER, events.single().type)
        assertEquals(Direction4.LEFT, events.single().direction)
        assertTrue(events.single().intensity in 0f..1f)
    }

    @Test
    fun leftTurnIsHardCornerLeaning_RIGHT() {
        val events = corner(-30)
        assertEquals(1, events.size)
        assertEquals(Direction4.RIGHT, events.single().direction)
    }

    @Test
    fun gentleAccelerationOver8SecondsIsNotHardAccel() {
        // 0 -> 50 km/h, 8초 (약 1.7 m/s²) (SC-004)
        val signals = scenario("gentle") { at(0) { speedRamp(0, 50, 8_000) } }.expand()
        assertTrue(AccelEventDetector(EngineConfig()).feed(signals).isEmpty())
        assertTrue(AccelEventDetector(raw).feed(signals).isEmpty())
    }

    @Test
    fun largeHeadingChangeBelow15KmhIsNotHardCorner() {
        // 10 km/h, 1초에 90도: 옆 가속도 약 4.4 m/s² 로 임계값을 넘지만 속도 조건으로 제외
        assertTrue(corner(90, kmh = 10.0).isEmpty())
        // 속도 조건을 풀면 같은 입력이 급커브가 된다(조건이 실제로 작동하는지 확인)
        assertEquals(1, corner(90, EngineConfig(cornerMinSpeedMps = 0.0), kmh = 10.0).size)
    }

    @Test
    fun singleSampleOverThresholdIsNotConfirmed() {
        val over = speeds(0L to 0f, 100L to 0f, 200L to 0.5f, 300L to 0.5f, 400L to 0.5f)
        assertTrue(AccelEventDetector(raw).feed(over).isEmpty()) // 5 m/s² 한 샘플만

        val twice = speeds(0L to 0f, 100L to 0.5f, 200L to 1.0f) // 5 m/s² 두 샘플 연속
        val events = AccelEventDetector(raw).feed(twice)
        assertEquals(1, events.size)
        assertEquals(EventType.HARD_ACCEL, events.single().type)
        assertEquals(200L, events.single().atMs)
        assertEquals(Direction4.BACK, events.single().direction)
    }

    @Test
    fun belowThresholdSampleResetsConsecutiveCount() {
        // over, under, over: 연속이 아니므로 확정되지 않음
        val s = speeds(0L to 0f, 100L to 0.5f, 100L + 100 to 0.5f, 300L to 1.0f)
        assertTrue(AccelEventDetector(raw).feed(s).isEmpty())
    }

    @Test
    fun sameKindWithinCooldownIsRecognizedOnce() {
        val det = AccelEventDetector(raw)
        det.onBrakePedal(true)
        val events = det.feed(
            speeds(
                0L to 20f, 100L to 20f, 200L to 19f, 300L to 18f, // 확정 @300
                400L to 18f, 500L to 17f, 600L to 16f, // 재발 간격 안: 무시
                1500L to 16f, 2500L to 16f, 3500L to 16f,
                3600L to 15f, 3700L to 14f, // 간격 지남: 다시 확정
            ),
        )
        assertEquals(listOf(300L, 3700L), events.map { it.atMs })
        assertTrue(events.all { it.type == EventType.HARD_BRAKE })
    }

    @Test
    fun intensityIsNormalizedBetweenThresholdAndMax() {
        // 가속도 -5.85 m/s² -> (5.85 - 3.9) / (7.8 - 3.9) = 0.5
        val det = AccelEventDetector(raw)
        det.onBrakePedal(true)
        val e = det.feed(speeds(0L to 20f, 1000L to 14.15f, 2000L to 8.3f)).single()
        assertEquals(0.5f, e.intensity, 1e-3f)
    }

    @Test
    fun intensityIsClampedToOne() {
        val det = AccelEventDetector(raw)
        det.onBrakePedal(true)
        val e = det.feed(speeds(0L to 30f, 100L to 20f, 200L to 10f)).single() // -100 m/s²
        assertEquals(1f, e.intensity, 0f)
    }

    @Test
    fun thresholdsComeFromEngineConfig() {
        assertEquals(1, hardBrake().size)
        assertTrue(hardBrake(EngineConfig(hardBrakeThresholdMps2 = 8.0)).isEmpty())
        assertEquals(1, corner(30).size)
        assertTrue(corner(30, EngineConfig(hardCornerThresholdMps2 = 20.0)).isEmpty())
    }

    @Test
    fun hardAccelIntensityUsesAccelThreshold() {
        // 4 m/s² -> (4 - 2.9) / (7.8 - 2.9)
        val e = AccelEventDetector(raw).feed(speeds(0L to 0f, 1000L to 4f, 2000L to 8f)).single()
        assertEquals(((4.0 - 2.9) / (7.8 - 2.9)).toFloat(), e.intensity, 1e-4f)
    }
}
