package com.example.dashpet.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class KinematicsTest {
    private val raw = EngineConfig(smoothingAlpha = 1.0) // 평활화 없음

    @Test
    fun longitudinalAccelIsSpeedChangeRate() {
        val k = Kinematics(raw)
        assertNull(k.onSpeed(0, 0.0)) // 첫 샘플은 기준선
        assertEquals(5.0, k.onSpeed(1000, 5.0)!!, 1e-9)
        assertEquals(-2.0, k.onSpeed(2000, 3.0)!!, 1e-9)
    }

    @Test
    fun speedIsSmoothedWithEma() {
        val k = Kinematics(EngineConfig()) // alpha 0.4
        k.onSpeed(0, 10.0)
        // 평활 속도: 0.4 * 20 + 0.6 * 10 = 14
        assertEquals(4.0, k.onSpeed(1000, 20.0)!!, 1e-9)
        assertEquals(14.0, k.latestSpeedMps!!, 1e-9)
    }

    @Test
    fun angleDiffWrapsAroundNorth() {
        assertEquals(2.0, Kinematics.angleDiffDeg(359.0, 1.0), 1e-9)
        assertEquals(-2.0, Kinematics.angleDiffDeg(1.0, 359.0), 1e-9)
        assertEquals(30.0, Kinematics.angleDiffDeg(90.0, 120.0), 1e-9)
    }

    @Test
    fun lateralAccelIsYawRateTimesSpeed() {
        val k = Kinematics(raw)
        k.onSpeed(0, 10.0)
        assertNull(k.onHeading(0, 359.0))
        // 359 -> 1 은 +2도. 1초 동안이므로 2도/초 = 0.0349 rad/s, 속도 10 m/s
        assertEquals(Math.toRadians(2.0) * 10.0, k.onHeading(1000, 1.0)!!, 1e-9)
        // 반대 방향은 음수
        assertEquals(-Math.toRadians(2.0) * 10.0, k.onHeading(2000, 359.0)!!, 1e-9)
    }

    @Test
    fun headingIsUnwrappedBeforeSmoothing() {
        val k = Kinematics(EngineConfig()) // alpha 0.4
        k.onSpeed(0, 10.0)
        k.onHeading(0, 359.0)
        // 펼친 값 361 -> 평활 0.4 * 361 + 0.6 * 359 = 359.8, 변화 +0.8도 (펼치지 않으면 -358도 근처로 튐)
        assertEquals(Math.toRadians(0.8) * 10.0, k.onHeading(1000, 1.0)!!, 1e-9)
    }

    @Test
    fun speedGapOverLimitDropsSampleAndResetsBaseline() {
        val k = Kinematics(raw)
        k.onSpeed(0, 0.0)
        assertNull(k.onSpeed(2001, 10.0)) // 2초 초과: 버리고 기준선 초기화
        assertEquals(10.0, k.onSpeed(2101, 11.0)!!, 1e-9) // 새 기준선(10) 기준
        assertNotNull(k.onSpeed(2201, 11.0))
    }

    @Test
    fun gapExactlyAtLimitIsStillValid() {
        val k = Kinematics(raw)
        k.onSpeed(0, 0.0)
        assertEquals(2.5, k.onSpeed(2000, 5.0)!!, 1e-9)
    }

    @Test
    fun headingGapOverLimitResets() {
        val k = Kinematics(raw)
        k.onSpeed(0, 10.0)
        k.onHeading(0, 0.0)
        assertNull(k.onHeading(2500, 90.0))
        assertEquals(Math.toRadians(10.0) / 0.1 * 10.0, k.onHeading(2600, 100.0)!!, 1e-9)
    }

    @Test
    fun headingWithoutSpeedInfoIsDroppedAndBaselineReset() {
        val k = Kinematics(raw)
        assertNull(k.onHeading(0, 0.0)) // 속도 정보 없음: 버림
        k.onSpeed(50, 10.0)
        assertNull(k.onHeading(100, 10.0)) // 직전 샘플은 버려졌으므로 이것이 새 기준선
        assertNotNull(k.onHeading(200, 11.0))
    }
}
