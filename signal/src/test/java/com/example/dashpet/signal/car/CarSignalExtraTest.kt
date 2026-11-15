package com.example.dashpet.signal.car

import com.example.dashpet.signal.BrakePedal
import com.example.dashpet.signal.Heading
import com.example.dashpet.signal.RawSignal
import com.example.dashpet.signal.Restriction
import com.example.dashpet.signal.RestrictionState
import com.example.dashpet.signal.Tick
import com.example.dashpet.signal.TurnSignal
import com.example.dashpet.signal.TurnState
import com.example.dashpet.signal.VehicleWarning
import com.example.dashpet.signal.WarningType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 브레이크 페달, 방향지시등, 안전벨트 경고, 방위각(specs/005). 신호별로 서로 독립이다. */
@OptIn(ExperimentalCoroutinesApi::class)
class CarSignalExtraTest {
    private fun mapper(vararg values: Long): CarSignalMapper {
        val queue = ArrayDeque(values.toList())
        return CarSignalMapper(clock = { queue.removeFirst() }, startMs = 0L)
    }

    // --- 매퍼 ---

    @Test fun brakePedalMapsAndDedupes() {
        val m = mapper(10, 20, 30)
        assertEquals(BrakePedal(10, true), m.brake(true))
        assertNull(m.brake(true))
        assertEquals(BrakePedal(30, false), m.brake(false))
    }

    @Test fun turnSignalMapsAndDedupes() {
        val m = mapper(10, 20, 30, 40)
        assertEquals(TurnSignal(10, TurnState.LEFT), m.turnSignal(TurnState.LEFT))
        assertNull(m.turnSignal(TurnState.LEFT))
        assertEquals(TurnSignal(30, TurnState.OFF), m.turnSignal(TurnState.OFF))
        assertEquals(TurnSignal(40, TurnState.RIGHT), m.turnSignal(TurnState.RIGHT))
    }

    @Test fun seatbeltWarningMapsToVehicleWarningAndDedupes() {
        val m = mapper(10, 20, 30)
        assertEquals(VehicleWarning(10, WarningType.SEATBELT, true), m.seatbelt(true))
        assertNull(m.seatbelt(true))
        assertEquals(VehicleWarning(30, WarningType.SEATBELT, false), m.seatbelt(false))
    }

    @Test fun headingAcceptsZeroTo360AndDoesNotDedupe() {
        val m = mapper(10, 20, 30)
        assertEquals(Heading(10, 90f), m.heading(90f))
        assertEquals(Heading(20, 90f), m.heading(90f)) // 같은 값도 시각이 진행되어야 한다(엔진 샘플 간격 규칙)
        assertEquals(Heading(30, 0f), m.heading(360f)) // 360 은 0 으로 정규화된다
    }

    @Test fun headingRejectsUnknownValues() {
        val m = mapper(10, 20, 30, 40)
        assertNull(m.heading(Float.NaN))
        assertNull(m.heading(-1f)) // 방위를 모를 때 흔한 표식. 359 로 오해하지 않는다 [확인 필요]
        assertNull(m.heading(361f))
        assertNull(m.heading(Float.POSITIVE_INFINITY))
    }

    @Test fun lostResetsDedupeSoRecoveredValuesAreSentAgain() {
        val m = mapper(10, 20, 30)
        assertEquals(BrakePedal(10, true), m.brake(true))
        m.lost() // 시계를 한 번 읽는다(20)
        assertEquals(BrakePedal(30, true), m.brake(true))
    }

    // --- 소스 ---

    private class Rig(scope: kotlinx.coroutines.test.TestScope) {
        val gateway = FakeCarGateway()
        val source = CarSignalSource(gateway, clock = { scope.currentTime }, config = CarSignalConfig(tickIntervalMs = 0))
        val got = mutableListOf<RawSignal>()

        init {
            scope.backgroundScope.launch(UnconfinedTestDispatcher(scope.testScheduler)) { source.signals.collect { got += it } }
        }

        val signals: List<RawSignal> get() = got.filter { it !is Tick }
    }

    @Test fun sourceMapsAllNewEvents() = runTest {
        val rig = Rig(this)
        rig.source.start()
        rig.gateway.emit(CarEvent.Ready)
        rig.gateway.emit(CarEvent.BrakePedalChanged(true))
        rig.gateway.emit(CarEvent.TurnSignalChanged(TurnState.RIGHT))
        rig.gateway.emit(CarEvent.SeatbeltWarningChanged(true))
        rig.gateway.emit(CarEvent.HeadingChanged(45f))
        runCurrent()
        assertEquals(
            listOf<RawSignal>(
                BrakePedal(0, true),
                TurnSignal(0, TurnState.RIGHT),
                VehicleWarning(0, WarningType.SEATBELT, true),
                Heading(0, 45f),
            ),
            rig.signals,
        )
    }

    @Test fun diagnosticsCarryNewSignalsAndAvailability() = runTest {
        val rig = Rig(this)
        rig.source.start()
        rig.gateway.emit(CarEvent.Ready)
        rig.gateway.emit(CarEvent.AvailabilityChanged(CarSignal.BRAKE, SignalAvailability.OK))
        rig.gateway.emit(CarEvent.AvailabilityChanged(CarSignal.HEADING, SignalAvailability.NO_PERMISSION))
        rig.gateway.emit(CarEvent.BrakePedalChanged(true))
        runCurrent()
        val d = rig.source.diagnostics.value
        assertEquals(SignalAvailability.OK, d.brakeAvailability)
        assertEquals(SignalAvailability.NO_PERMISSION, d.headingAvailability)
        assertEquals(true, d.brakePressed)
        assertEquals(SignalAvailability.UNKNOWN, d.turnSignalAvailability)
    }

    @Test fun newSignalsDoNotChangeRestrictionHandling() = runTest {
        val rig = Rig(this)
        rig.source.start()
        rig.gateway.emit(CarEvent.Ready)
        rig.gateway.emit(CarEvent.BrakePedalChanged(true))
        runCurrent()
        // 제한 값을 받기 전에는 제한 신호를 내지 않는다(엔진 기본값이 UNKNOWN = 정적).
        assertEquals(false, rig.signals.any { it is Restriction })
        rig.gateway.emit(CarEvent.RestrictionChanged(false, 0))
        runCurrent()
        assertEquals(Restriction(0, RestrictionState.NONE), rig.signals.filterIsInstance<Restriction>().single())
    }

    @Test fun compositeGatewayMergesAndOpensAllChildren() = runTest {
        val primary = FakeCarGateway()
        val extra = FakeCarGateway()
        val composite = CompositeCarGateway(primary, listOf(extra))
        val got = mutableListOf<CarEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { composite.events().collect { got += it } }
        runCurrent()
        primary.emit(CarEvent.SpeedChanged(1f))
        extra.emit(CarEvent.BrakePedalChanged(true))
        runCurrent()
        assertEquals(listOf<CarEvent>(CarEvent.SpeedChanged(1f), CarEvent.BrakePedalChanged(true)), got)
        assertEquals(1, primary.openCount); assertEquals(1, extra.openCount)
    }

    @Test fun failingExtraGatewayDoesNotStopThePrimary() = runTest {
        val primary = FakeCarGateway()
        val extra = FakeCarGateway()
        val composite = CompositeCarGateway(primary, listOf(extra))
        val got = mutableListOf<CarEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { composite.events().collect { got += it } }
        runCurrent()
        extra.fail()
        runCurrent()
        primary.emit(CarEvent.RestrictionChanged(true, 0x50))
        runCurrent()
        assertEquals(true, got.contains(CarEvent.RestrictionChanged(true, 0x50)))
    }

    @Test fun failingPrimaryGatewayEndsTheFlowSoRestrictionBecomesUnknown() = runTest {
        val primary = FakeCarGateway()
        val composite = CompositeCarGateway(primary, emptyList())
        var failed = false
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            try { composite.events().collect { } } catch (e: IllegalStateException) { failed = true }
        }
        runCurrent()
        primary.fail()
        runCurrent()
        assertEquals(true, failed)
    }
}
