package com.example.dashpet.signal.car

import com.example.dashpet.signal.ParkedState
import com.example.dashpet.signal.RawSignal
import com.example.dashpet.signal.Restriction
import com.example.dashpet.signal.RestrictionState
import com.example.dashpet.signal.Speed
import com.example.dashpet.signal.Tick
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicLong

@OptIn(ExperimentalCoroutinesApi::class)
class CarSignalSourceTest {
    private class Rig(
        scope: TestScope,
        config: CarSignalConfig = CarSignalConfig(tickIntervalMs = 0),
        clock: () -> Long = { scope.currentTime },
    ) {
        val gateway = FakeCarGateway()
        val source = CarSignalSource(gateway, clock = clock, config = config)
        val got = mutableListOf<RawSignal>()
        val job: Job = scope.backgroundScope.launch(UnconfinedTestDispatcher(scope.testScheduler)) {
            source.signals.collect { got += it }
        }

        /** 시나리오 신호만(Tick 제외). */
        val signals: List<RawSignal> get() = got.filter { it !is Tick }
    }

    @Test fun emitsNothingBeforeStart() = runTest {
        val rig = Rig(this)
        rig.gateway.emit(CarEvent.Ready)
        rig.gateway.emit(CarEvent.RestrictionChanged(true, 0x50))
        runCurrent()
        assertTrue(rig.got.isEmpty())
        assertEquals(0, rig.gateway.openCount)
        assertEquals(CarLinkState.IDLE, rig.source.diagnostics.value.link)
    }

    @Test fun readyThenRestrictionProducesOneRestriction() = runTest {
        val rig = Rig(this)
        rig.source.start()
        rig.gateway.emit(CarEvent.Ready)
        rig.gateway.emit(CarEvent.RestrictionChanged(requiresDo = true, activeRestrictions = 0x50))
        runCurrent()
        assertEquals(listOf<RawSignal>(Restriction(0, RestrictionState.ACTIVE)), rig.signals)
        assertEquals(CarLinkState.CONNECTED, rig.source.diagnostics.value.link)
        assertEquals(RestrictionState.ACTIVE, rig.source.diagnostics.value.restriction)
        assertEquals(0x50, rig.source.diagnostics.value.activeRestrictions)
    }

    @Test fun repeatedSameValueIsEmittedOnceAndChangeIsEmitted() = runTest {
        val rig = Rig(this)
        rig.source.start()
        rig.gateway.emit(CarEvent.Ready)
        rig.gateway.emit(CarEvent.RestrictionChanged(true, 0x50))
        rig.gateway.emit(CarEvent.RestrictionChanged(true, 0x50))
        advanceTimeBy(100)
        rig.gateway.emit(CarEvent.RestrictionChanged(false, 0))
        runCurrent()
        assertEquals(
            listOf<RawSignal>(Restriction(0, RestrictionState.ACTIVE), Restriction(100, RestrictionState.NONE)),
            rig.signals,
        )
    }

    @Test fun noRestrictionBeforeTheFirstValueArrives() = runTest {
        val rig = Rig(this)
        rig.source.start()
        rig.gateway.emit(CarEvent.Ready)
        advanceTimeBy(5_000)
        runCurrent()
        // 엔진의 기본값(UNKNOWN)을 그대로 쓰므로 소스가 따로 신호를 내지 않는다.
        assertTrue(rig.signals.isEmpty())
    }

    @Test fun lostMeansUnknownThenRecoveryReadsCurrentValueAgain() = runTest {
        val rig = Rig(this)
        rig.source.start()
        rig.gateway.emit(CarEvent.Ready)
        rig.gateway.emit(CarEvent.RestrictionChanged(false, 0))
        advanceTimeBy(100)
        rig.gateway.emit(CarEvent.Lost)
        advanceTimeBy(100)
        rig.gateway.emit(CarEvent.Ready)
        rig.gateway.emit(CarEvent.RestrictionChanged(false, 0)) // 복구 직후 같은 값
        runCurrent()
        assertEquals(
            listOf<RawSignal>(
                Restriction(0, RestrictionState.NONE),
                Restriction(100, RestrictionState.UNKNOWN),
                Restriction(200, RestrictionState.NONE),
            ),
            rig.signals,
        )
        assertEquals(CarLinkState.CONNECTED, rig.source.diagnostics.value.link)
    }

    @Test fun lostShowsInDiagnosticsAsLost() = runTest {
        val rig = Rig(this)
        rig.source.start()
        rig.gateway.emit(CarEvent.Ready)
        rig.gateway.emit(CarEvent.Lost)
        runCurrent()
        assertEquals(CarLinkState.LOST, rig.source.diagnostics.value.link)
    }

    @Test fun tickEveryIntervalAndNeverBeforeEarlierSignals() = runTest {
        val rig = Rig(this, CarSignalConfig(tickIntervalMs = 250))
        rig.source.start()
        rig.gateway.emit(CarEvent.Ready)
        rig.gateway.emit(CarEvent.RestrictionChanged(true, 0x50))
        advanceTimeBy(1_001)
        runCurrent()
        val ticks = rig.got.filterIsInstance<Tick>().map { it.timestampMs }
        assertEquals(listOf(250L, 500L, 750L, 1_000L), ticks)
        val times = rig.got.map { it.timestampMs }
        assertEquals(times.sorted(), times) // 감소하지 않음
    }

    @Test fun stopClosesGatewayAndIgnoresLaterEvents() = runTest {
        val rig = Rig(this)
        rig.source.start()
        rig.gateway.emit(CarEvent.Ready)
        runCurrent()
        assertTrue(rig.gateway.isOpen)
        rig.source.stop()
        runCurrent()
        assertEquals(1, rig.gateway.closeCount)
        assertFalse(rig.gateway.isOpen)
        rig.gateway.emit(CarEvent.RestrictionChanged(true, 0x50))
        runCurrent()
        assertTrue(rig.signals.isEmpty())
        assertEquals(CarLinkState.IDLE, rig.source.diagnostics.value.link)
    }

    @Test fun cancellingTheCollectorClosesTheGateway() = runTest {
        val rig = Rig(this)
        rig.source.start()
        runCurrent()
        assertEquals(1, rig.gateway.openCount)
        rig.job.cancel()
        runCurrent()
        assertEquals(1, rig.gateway.closeCount)
    }

    @Test fun startingAgainOpensTheGatewayAnew() = runTest {
        val rig = Rig(this)
        rig.source.start()
        runCurrent()
        rig.source.stop()
        runCurrent()
        rig.source.start()
        runCurrent()
        assertEquals(2, rig.gateway.openCount)
        assertEquals(1, rig.gateway.closeCount)
        // 새 세션은 같은 값도 다시 한 번 낸다
        rig.gateway.emit(CarEvent.Ready)
        rig.gateway.emit(CarEvent.RestrictionChanged(true, 0x50))
        runCurrent()
        assertEquals(1, rig.signals.size)
    }

    @Test fun doubleStartDoesNotOpenTwice() = runTest {
        val rig = Rig(this)
        rig.source.start()
        rig.source.start()
        runCurrent()
        assertEquals(1, rig.gateway.openCount)
    }

    // --- 속도와 기어 (US2) ---

    @Test fun initialValuesAreEachProducedOnce() = runTest {
        val rig = Rig(this)
        rig.source.start()
        rig.gateway.emit(CarEvent.Ready)
        rig.gateway.emit(CarEvent.SpeedChanged(0f))
        rig.gateway.emit(CarEvent.GearChanged(4))
        rig.gateway.emit(CarEvent.RestrictionChanged(false, 0))
        runCurrent()
        assertEquals(
            listOf<RawSignal>(
                Speed(0, 0f),
                ParkedState(0, true),
                Restriction(0, RestrictionState.NONE),
            ),
            rig.signals,
        )
        val d = rig.source.diagnostics.value
        assertEquals(0f, d.speedMps)
        assertEquals(true, d.parked)
    }

    @Test fun sameTimestampOrderIsSpeedParkedRestrictionThenTick() = runTest {
        val rig = Rig(this, CarSignalConfig(tickIntervalMs = 250))
        rig.source.start()
        rig.gateway.emit(CarEvent.Ready)
        rig.gateway.emit(CarEvent.SpeedChanged(1f))
        rig.gateway.emit(CarEvent.GearChanged(8))
        rig.gateway.emit(CarEvent.RestrictionChanged(true, 0x50))
        advanceTimeBy(251)
        runCurrent()
        assertEquals(
            listOf<RawSignal>(
                Speed(0, 1f),
                ParkedState(0, false),
                Restriction(0, RestrictionState.ACTIVE),
                Tick(250),
            ),
            rig.got,
        )
    }

    @Test fun changesAreProducedAndRepeatsAreNot() = runTest {
        val rig = Rig(this)
        rig.source.start()
        rig.gateway.emit(CarEvent.Ready)
        rig.gateway.emit(CarEvent.SpeedChanged(3f))
        rig.gateway.emit(CarEvent.SpeedChanged(3f))
        rig.gateway.emit(CarEvent.GearChanged(8))
        rig.gateway.emit(CarEvent.GearChanged(2)) // R 도 주차 아님이므로 새 정보가 없다
        advanceTimeBy(50)
        rig.gateway.emit(CarEvent.SpeedChanged(4f))
        rig.gateway.emit(CarEvent.GearChanged(4))
        runCurrent()
        assertEquals(
            listOf<RawSignal>(Speed(0, 3f), ParkedState(0, false), Speed(50, 4f), ParkedState(50, true)),
            rig.signals,
        )
    }

    @Test fun invalidSpeedNeverReachesTheEngine() = runTest {
        val rig = Rig(this)
        rig.source.start()
        rig.gateway.emit(CarEvent.Ready)
        rig.gateway.emit(CarEvent.SpeedChanged(-3f))
        rig.gateway.emit(CarEvent.SpeedChanged(Float.NaN))
        rig.gateway.emit(CarEvent.GearChanged(0))
        runCurrent()
        assertTrue(rig.signals.isEmpty())
    }

    @Test fun timestampsNeverDecreaseEvenIfTheClockGoesBack() = runTest {
        val times = ArrayDeque(listOf(1_000L, 1_100L, 1_050L, 1_200L))
        val rig = Rig(this, clock = { times.removeFirst() }) // 첫 값은 소스 시작 시각
        rig.source.start()
        rig.gateway.emit(CarEvent.Ready)
        rig.gateway.emit(CarEvent.SpeedChanged(1f))
        rig.gateway.emit(CarEvent.SpeedChanged(2f))
        rig.gateway.emit(CarEvent.SpeedChanged(3f))
        runCurrent()
        assertEquals(listOf(100L, 100L, 200L), rig.signals.map { it.timestampMs })
    }

    @Test fun concurrentCallbacksKeepOrderAndLoseNothing() = runTest {
        val counter = AtomicLong()
        val rig = Rig(this, clock = { counter.incrementAndGet() })
        rig.source.start()
        rig.gateway.emit(CarEvent.Ready)
        runCurrent()
        coroutineScope {
            repeat(4) { k ->
                launch(Dispatchers.Default) {
                    repeat(100) { i -> rig.gateway.emit(CarEvent.SpeedChanged(k * 1_000f + i + 1)) }
                }
            }
        }
        runCurrent()
        val speeds = rig.signals.filterIsInstance<Speed>()
        assertEquals(400, speeds.size) // 값이 모두 달라 중복 제거에 걸리지 않는다
        val times = speeds.map { it.timestampMs }
        assertEquals(times.sorted(), times)
    }

    // --- 안전하게 정적으로 떨어지기 (US3) ---

    @Test fun restrictionErrorKeepsTheSourceAliveWithoutASignal() = runTest {
        val rig = Rig(this)
        rig.source.start()
        rig.gateway.emit(CarEvent.Ready)
        rig.gateway.emit(CarEvent.AvailabilityChanged(CarSignal.RESTRICTION, SignalAvailability.ERROR))
        advanceTimeBy(1_000)
        runCurrent()
        assertTrue(rig.signals.isEmpty()) // 엔진 기본값(UNKNOWN)이 그대로다
        assertTrue(rig.gateway.isOpen)
        assertEquals(SignalAvailability.ERROR, rig.source.diagnostics.value.restrictionAvailability)
        assertEquals(RestrictionState.UNKNOWN, rig.source.diagnostics.value.restriction)
    }

    @Test fun speedWithoutPermissionDoesNotStopTheOtherSignals() = runTest {
        val rig = Rig(this)
        rig.source.start()
        rig.gateway.emit(CarEvent.Ready)
        rig.gateway.emit(CarEvent.AvailabilityChanged(CarSignal.SPEED, SignalAvailability.NO_PERMISSION))
        rig.gateway.emit(CarEvent.GearChanged(4))
        rig.gateway.emit(CarEvent.RestrictionChanged(false, 0))
        runCurrent()
        assertEquals(
            listOf<RawSignal>(ParkedState(0, true), Restriction(0, RestrictionState.NONE)),
            rig.signals,
        )
        assertEquals(SignalAvailability.NO_PERMISSION, rig.source.diagnostics.value.speedAvailability)
    }

    @Test fun unsupportedSpeedDoesNotStopTheOtherSignals() = runTest {
        val rig = Rig(this)
        rig.source.start()
        rig.gateway.emit(CarEvent.Ready)
        rig.gateway.emit(CarEvent.AvailabilityChanged(CarSignal.SPEED, SignalAvailability.UNSUPPORTED))
        rig.gateway.emit(CarEvent.GearChanged(8))
        rig.gateway.emit(CarEvent.RestrictionChanged(true, 0x50))
        runCurrent()
        assertEquals(2, rig.signals.size)
        assertEquals(SignalAvailability.UNSUPPORTED, rig.source.diagnostics.value.speedAvailability)
    }

    @Test fun diagnosticsFollowEachSignalsAvailability() = runTest {
        val rig = Rig(this)
        rig.source.start()
        rig.gateway.emit(CarEvent.Ready)
        rig.gateway.emit(CarEvent.AvailabilityChanged(CarSignal.SPEED, SignalAvailability.OK))
        rig.gateway.emit(CarEvent.AvailabilityChanged(CarSignal.GEAR, SignalAvailability.UNSUPPORTED))
        rig.gateway.emit(CarEvent.AvailabilityChanged(CarSignal.RESTRICTION, SignalAvailability.NO_PERMISSION))
        runCurrent()
        val d = rig.source.diagnostics.value
        assertEquals(SignalAvailability.OK, d.speedAvailability)
        assertEquals(SignalAvailability.UNSUPPORTED, d.gearAvailability)
        assertEquals(SignalAvailability.NO_PERMISSION, d.restrictionAvailability)
    }

    @Test fun staleValueIsKeptWhileConnectedAndNeverTurnsUnknownByTime() = runTest {
        val rig = Rig(this)
        rig.source.start()
        rig.gateway.emit(CarEvent.Ready)
        rig.gateway.emit(CarEvent.RestrictionChanged(false, 0))
        runCurrent()
        val before = rig.source.diagnostics.value
        advanceTimeBy(10 * 60_000L) // 플랫폼은 값이 변할 때만 알리므로 10분 동안 조용할 수 있다
        runCurrent()
        assertEquals(1, rig.signals.size)
        val after = rig.source.diagnostics.value
        assertEquals(RestrictionState.NONE, after.restriction)
        assertEquals(CarLinkState.CONNECTED, after.link)
        assertEquals(before.lastUpdateMs, after.lastUpdateMs)
    }

    @Test fun gatewayFailureBeforeAnyValueLeavesRestrictionUnknown() = runTest {
        val rig = Rig(this)
        rig.source.start()
        rig.gateway.emit(CarEvent.Ready)
        rig.gateway.fail()
        runCurrent()
        // 신호가 나온다면 전부 UNKNOWN 이어야 한다(엔진 기본값과 같아 해롭지 않다).
        assertTrue(rig.signals.all { it is Restriction && it.state == RestrictionState.UNKNOWN })
        assertEquals(CarLinkState.UNAVAILABLE, rig.source.diagnostics.value.link)
        assertEquals(RestrictionState.UNKNOWN, rig.source.diagnostics.value.restriction)
    }

    @Test fun gatewayFailureAfterAValueMakesTheRestrictionUnknownAgain() = runTest {
        val rig = Rig(this)
        rig.source.start()
        rig.gateway.emit(CarEvent.Ready)
        rig.gateway.emit(CarEvent.RestrictionChanged(false, 0))
        advanceTimeBy(100)
        rig.gateway.fail()
        runCurrent()
        // 엔진이 이전 값(NONE = 풀 모션 가능)에 머물면 안전하지 않다.
        assertEquals(
            listOf<RawSignal>(Restriction(0, RestrictionState.NONE), Restriction(100, RestrictionState.UNKNOWN)),
            rig.signals,
        )
        assertEquals(RestrictionState.UNKNOWN, rig.source.diagnostics.value.restriction)
        assertEquals(CarLinkState.UNAVAILABLE, rig.source.diagnostics.value.link)
    }

    @Test fun failureDoesNotThrowOutOfTheSignalFlow() = runTest {
        val rig = Rig(this)
        rig.source.start()
        rig.gateway.emit(CarEvent.Ready)
        rig.gateway.fail()
        runCurrent()
        assertTrue(rig.job.isActive) // 수집이 예외로 끝나지 않았다
    }

    // --- 같은 값이 반복돼도 진단이 바뀌지 않는다 (T040 에서 발견: 정적 상태에서 화면이 계속 다시 그려지던 문제) ---

    @Test fun repeatedIdenticalValuesDoNotChangeDiagnostics() = runTest {
        val rig = Rig(this)
        rig.source.start()
        rig.gateway.emit(CarEvent.Ready)
        rig.gateway.emit(CarEvent.SpeedChanged(0f))
        rig.gateway.emit(CarEvent.GearChanged(8))
        rig.gateway.emit(CarEvent.RestrictionChanged(true, 0x50))
        runCurrent()
        val settled = rig.source.diagnostics.value
        val seen = mutableListOf<SignalDiagnostics>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { rig.source.diagnostics.collect { seen += it } }
        // 차량이 같은 값을 초당 여러 번 알려도(속도는 5Hz) 진단 값이 새로 나오면 화면이 계속 다시 그려진다.
        repeat(50) {
            advanceTimeBy(200)
            rig.gateway.emit(CarEvent.SpeedChanged(0f))
            rig.gateway.emit(CarEvent.GearChanged(8))
            rig.gateway.emit(CarEvent.RestrictionChanged(true, 0x50))
        }
        runCurrent()
        assertEquals(listOf(settled), seen) // 수집을 시작할 때의 현재 값 하나뿐
        assertEquals(settled, rig.source.diagnostics.value)
    }

    @Test fun lastUpdateMovesOnlyWhenAValueActuallyChanges() = runTest {
        val rig = Rig(this)
        rig.source.start()
        rig.gateway.emit(CarEvent.Ready)
        rig.gateway.emit(CarEvent.SpeedChanged(1f))
        runCurrent()
        assertEquals(0L, rig.source.diagnostics.value.lastUpdateMs)
        advanceTimeBy(500)
        rig.gateway.emit(CarEvent.SpeedChanged(1f)) // 같은 값
        runCurrent()
        assertEquals(0L, rig.source.diagnostics.value.lastUpdateMs)
        advanceTimeBy(500)
        rig.gateway.emit(CarEvent.SpeedChanged(2f)) // 바뀐 값
        runCurrent()
        assertEquals(1_000L, rig.source.diagnostics.value.lastUpdateMs)
        assertEquals(2f, rig.source.diagnostics.value.speedMps)
    }

    @Test fun changedBitsWithTheSameStateUpdateOnlyTheBits() = runTest {
        val rig = Rig(this)
        rig.source.start()
        rig.gateway.emit(CarEvent.Ready)
        rig.gateway.emit(CarEvent.RestrictionChanged(true, 0x50))
        runCurrent()
        advanceTimeBy(300)
        rig.gateway.emit(CarEvent.RestrictionChanged(true, 0x1ff)) // 같은 ACTIVE, 비트만 다름
        runCurrent()
        val d = rig.source.diagnostics.value
        assertEquals(0x1ff, d.activeRestrictions)
        assertEquals(RestrictionState.ACTIVE, d.restriction)
        assertEquals(0L, d.lastUpdateMs) // 엔진에 새 값이 가지 않았으므로 갱신 시각은 그대로
    }
}
