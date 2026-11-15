package com.example.dashpet.signal.car

import com.example.dashpet.signal.ParkedState
import com.example.dashpet.signal.Restriction
import com.example.dashpet.signal.RestrictionState
import com.example.dashpet.signal.Speed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CarSignalMapperTest {
    /** 시계가 [values] 를 순서대로 돌려준다. 시작 시각은 0. */
    private fun mapper(vararg values: Long): CarSignalMapper {
        val queue = ArrayDeque(values.toList())
        return CarSignalMapper(clock = { queue.removeFirst() }, startMs = 0L)
    }

    // --- 제한 매핑 (US1) ---

    @Test fun requiresDoWithoutBitsIsActive() {
        assertEquals(Restriction(10, RestrictionState.ACTIVE), mapper(10).restriction(requiresDo = true, active = 0))
    }

    @Test fun bitsWithoutRequiresDoIsActive() {
        assertEquals(Restriction(10, RestrictionState.ACTIVE), mapper(10).restriction(requiresDo = false, active = 0x50))
    }

    @Test fun neitherRequiresDoNorBitsIsNone() {
        assertEquals(Restriction(10, RestrictionState.NONE), mapper(10).restriction(requiresDo = false, active = 0))
    }

    @Test fun requiresDoWithAllBitsIsActive() {
        assertEquals(Restriction(10, RestrictionState.ACTIVE), mapper(10).restriction(requiresDo = true, active = 0x1ff))
    }

    @Test fun lostMeansUnknown() {
        assertEquals(Restriction(10, RestrictionState.UNKNOWN), mapper(10).lost())
    }

    // --- 중복 제거 ---

    @Test fun sameRestrictionIsNotProducedTwice() {
        val m = mapper(10, 20, 30)
        assertEquals(Restriction(10, RestrictionState.ACTIVE), m.restriction(true, 0x50))
        assertNull(m.restriction(true, 0x50))
        assertEquals(Restriction(30, RestrictionState.NONE), m.restriction(false, 0))
    }

    @Test fun differentBitsWithSameStateIsStillTheSameRestriction() {
        val m = mapper(10, 20)
        assertEquals(Restriction(10, RestrictionState.ACTIVE), m.restriction(true, 0x50))
        assertNull(m.restriction(true, 0x1ff)) // ACTIVE → ACTIVE 이므로 엔진에는 새 정보가 없다
    }

    @Test fun lostIsProducedOnceAndRecoveryReproducesTheSameValue() {
        val m = mapper(10, 20, 30, 40)
        assertEquals(Restriction(10, RestrictionState.ACTIVE), m.restriction(true, 0x50))
        assertEquals(Restriction(20, RestrictionState.UNKNOWN), m.lost())
        assertNull(m.lost())
        // 복구 직후 같은 값(ACTIVE)이 와도 마지막으로 보낸 값은 UNKNOWN 이므로 다시 보낸다
        assertEquals(Restriction(40, RestrictionState.ACTIVE), m.restriction(true, 0x50))
    }

    @Test fun newSessionProducesTheSameValueOnceMore() {
        assertEquals(Restriction(10, RestrictionState.ACTIVE), mapper(10).restriction(true, 0x50))
        assertEquals(Restriction(10, RestrictionState.ACTIVE), mapper(10).restriction(true, 0x50))
    }

    // --- 시각 (FR-008) ---

    @Test fun timestampsNeverDecrease() {
        val m = mapper(100, 90, 150)
        assertEquals(100L, m.now())
        assertEquals(100L, m.now()) // 시계가 90 으로 되돌아가도 100 을 유지
        assertEquals(150L, m.now())
    }

    @Test fun timestampsStartFromZeroAtSessionStart() {
        val queue = ArrayDeque(listOf(5_250L))
        val m = CarSignalMapper(clock = { queue.removeFirst() }, startMs = 5_000L)
        assertEquals(250L, m.now())
    }

    @Test fun sameTimeIsAllowed() {
        val m = mapper(100, 100)
        assertEquals(100L, m.now())
        assertEquals(100L, m.now())
    }

    // --- 속도 (US2) ---

    @Test fun validSpeedIsProducedAsIs() {
        assertEquals(Speed(10, 13.9f), mapper(10).speed(13.9f))
    }

    @Test fun zeroSpeedPasses() {
        assertEquals(Speed(10, 0f), mapper(10).speed(0f))
    }

    @Test fun tinyNegativeSpeedIsCorrectedToZero() {
        assertEquals(Speed(10, 0f), mapper(10).speed(-0.0005f))
        assertEquals(Speed(10, 0f), mapper(10).speed(-CarSignalMapper.SPEED_EPSILON_MPS))
    }

    @Test fun otherNegativeNaNAndInfiniteSpeedsAreDropped() {
        val m = mapper(1, 2, 3, 4, 5)
        assertNull(m.speed(-0.5f))
        assertNull(m.speed(-1f))
        assertNull(m.speed(Float.NaN))
        assertNull(m.speed(Float.POSITIVE_INFINITY))
        assertNull(m.speed(Float.NEGATIVE_INFINITY))
    }

    @Test fun sameSpeedIsNotProducedTwiceButChangeIs() {
        val m = mapper(10, 20, 30)
        assertEquals(Speed(10, 5f), m.speed(5f))
        assertNull(m.speed(5f))
        assertEquals(Speed(30, 6f), m.speed(6f))
    }

    @Test fun droppedSpeedDoesNotResetDeduplication() {
        val m = mapper(10, 20, 30)
        assertEquals(Speed(10, 5f), m.speed(5f))
        assertNull(m.speed(-9f)) // 버려진 값
        assertNull(m.speed(5f)) // 여전히 직전에 보낸 값과 같다
    }

    @Test fun newSessionProducesTheSameSpeedOnceMore() {
        assertEquals(Speed(10, 5f), mapper(10).speed(5f))
        assertEquals(Speed(10, 5f), mapper(10).speed(5f))
    }

    // --- 기어 (US2) ---

    @Test fun parkIsParked() {
        assertEquals(ParkedState(10, true), mapper(10).gear(GEAR_PARK))
    }

    @Test fun driveReverseNeutralAreNotParked() {
        assertEquals(ParkedState(10, false), mapper(10).gear(GEAR_DRIVE))
        assertEquals(ParkedState(10, false), mapper(10).gear(GEAR_REVERSE))
        assertEquals(ParkedState(10, false), mapper(10).gear(GEAR_NEUTRAL))
    }

    @Test fun otherKnownGearsAreNotParked() {
        assertEquals(ParkedState(10, false), mapper(10).gear(0x10)) // GEAR_1 등 0 이 아닌 값
        assertEquals(ParkedState(10, false), mapper(10).gear(0x1000))
    }

    @Test fun unknownGearProducesNothing() {
        assertNull(mapper(10).gear(GEAR_UNKNOWN))
    }

    @Test fun sameParkedStateIsNotProducedTwiceEvenIfGearDiffers() {
        val m = mapper(10, 20, 30, 40)
        assertEquals(ParkedState(10, false), m.gear(GEAR_DRIVE))
        assertNull(m.gear(GEAR_REVERSE)) // 둘 다 주차 아님
        assertEquals(ParkedState(30, true), m.gear(GEAR_PARK))
        assertEquals(ParkedState(40, false), m.gear(GEAR_NEUTRAL))
    }

    @Test fun newSessionProducesTheSameParkedStateOnceMore() {
        assertEquals(ParkedState(10, true), mapper(10).gear(GEAR_PARK))
        assertEquals(ParkedState(10, true), mapper(10).gear(GEAR_PARK))
    }

    private companion object {
        // android.car.VehicleGear 값(스텁에서 확인). 이 JVM 테스트는 android.car 를 쓸 수 없어 값으로 적는다.
        const val GEAR_UNKNOWN = 0
        const val GEAR_NEUTRAL = 1
        const val GEAR_REVERSE = 2
        const val GEAR_PARK = 4
        const val GEAR_DRIVE = 8
    }
}
