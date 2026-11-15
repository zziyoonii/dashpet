package com.example.dashpet.ui.demo

import com.example.dashpet.engine.MotionTier
import com.example.dashpet.engine.Mood
import com.example.dashpet.signal.Pacing
import com.example.dashpet.signal.RawSignal
import com.example.dashpet.signal.Restriction
import com.example.dashpet.signal.RestrictionState
import com.example.dashpet.signal.VehicleSignalSource
import com.example.dashpet.signal.car.CarLinkState
import com.example.dashpet.signal.car.SignalDiagnostics
import com.example.dashpet.signal.scenario.DemoScenarios
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 실차 소스를 대신하는 가짜. 신호를 직접 넣고 start/stop 횟수를 센다. */
internal class FakeVehicleSource : VehicleSignalSource {
    val flow = MutableSharedFlow<RawSignal>(extraBufferCapacity = 64)
    var started = 0
    var stopped = 0
    override val signals: Flow<RawSignal> get() = flow
    override fun start() { started++ }
    override fun stop() { stopped++ }
}

@OptIn(ExperimentalCoroutinesApi::class)
class DemoViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    /** 기존 데모 케이스는 데모 모드에서 확인한다. */
    private fun vm(pacing: Pacing = Pacing.REAL_TIME, debug: Boolean = true) = DemoViewModel(
        vehicleSource = FakeVehicleSource(),
        scenario = DemoScenarios.demoCommute,
        pacing = pacing,
        debugAvailable = debug,
        initialMode = SignalMode.DEMO,
    )

    private fun vehicleVm(
        source: FakeVehicleSource = FakeVehicleSource(),
        debug: Boolean = true,
        diagnostics: MutableStateFlow<SignalDiagnostics>? = null,
    ) = DemoViewModel(vehicleSource = source, debugAvailable = debug, diagnostics = diagnostics)

    @Test fun initialStateIsStopped() = runTest {
        val vm = vm()
        runCurrent()
        assertEquals(PlaybackState.STOPPED, vm.demo.value.playback)
        assertEquals(0L, vm.demo.value.elapsedMs)
        assertNull(vm.demo.value.manualRestriction)
        assertTrue(vm.demo.value.debugAvailable)
    }

    @Test fun togglePlayStartsThenStops() = runTest {
        val vm = vm()
        runCurrent()
        vm.togglePlay()
        runCurrent()
        assertEquals(PlaybackState.PLAYING, vm.demo.value.playback)
        advanceTimeBy(1_050)
        runCurrent()
        assertEquals(1_000L, vm.demo.value.elapsedMs)
        vm.togglePlay()
        runCurrent()
        assertEquals(PlaybackState.STOPPED, vm.demo.value.playback)
        assertEquals(0L, vm.demo.value.elapsedMs)
    }

    @Test fun playbackReachesTheEndAndStops() = runTest {
        val vm = vm()
        runCurrent()
        vm.togglePlay()
        advanceTimeBy(54_000)
        runCurrent()
        assertEquals(PlaybackState.PLAYING, vm.demo.value.playback)
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(PlaybackState.STOPPED, vm.demo.value.playback)
    }

    @Test fun immediatePacingProducesTheDemoEvents() = runTest {
        val vm = vm(Pacing.IMMEDIATE)
        val seen = mutableListOf<com.example.dashpet.engine.EventType>()
        backgroundScope.launch(Dispatchers.Main.immediate) { vm.events.collect { seen += it.type } }
        runCurrent()
        vm.togglePlay()
        advanceTimeBy(60_000)
        runCurrent()
        assertTrue(seen.contains(com.example.dashpet.engine.EventType.HARD_BRAKE))
        assertEquals(PlaybackState.STOPPED, vm.demo.value.playback)
    }

    @Test fun stopReturnsPetToCalmAndResetsSession() = runTest {
        val vm = vm(Pacing.IMMEDIATE)
        runCurrent()
        vm.togglePlay()
        runCurrent()
        vm.stop()
        runCurrent()
        assertEquals(Mood.IDLE, vm.output.value.mood)
        assertEquals(PlaybackState.STOPPED, vm.demo.value.playback)
        assertNull(vm.demo.value.manualRestriction)
    }

    @Test fun restrictionChangesTierEvenWhenNotPlaying() = runTest {
        val vm = vm()
        runCurrent()
        vm.setRestriction(RestrictionState.NONE)
        runCurrent()
        assertEquals(MotionTier.FULL, vm.output.value.tier)
        assertEquals(RestrictionState.NONE, vm.demo.value.manualRestriction)
        vm.setRestriction(RestrictionState.ACTIVE)
        runCurrent()
        assertEquals(MotionTier.STATIC, vm.output.value.tier)
        vm.setRestriction(RestrictionState.UNKNOWN)
        runCurrent()
        assertEquals(MotionTier.STATIC, vm.output.value.tier)
        assertEquals(RestrictionState.UNKNOWN, vm.demo.value.manualRestriction)
    }

    @Test fun restrictionIgnoredWhenDebugUnavailable() = runTest {
        val vm = vm(debug = false)
        runCurrent()
        vm.setRestriction(RestrictionState.NONE)
        runCurrent()
        assertEquals(MotionTier.STATIC, vm.output.value.tier)
        assertNull(vm.demo.value.manualRestriction)
        assertEquals(false, vm.demo.value.debugAvailable)
    }

    @Test fun newSessionClearsManualRestrictionAndRestartsFromBeginning() = runTest {
        val vm = vm()
        runCurrent()
        vm.setRestriction(RestrictionState.NONE)
        runCurrent()
        vm.togglePlay()
        runCurrent()
        assertNull(vm.demo.value.manualRestriction)
        advanceTimeBy(500)
        vm.stop()
        vm.togglePlay()
        runCurrent()
        assertEquals(0L, vm.demo.value.elapsedMs)
        assertEquals(PlaybackState.PLAYING, vm.demo.value.playback)
    }

    @Test fun debugPanelVisibilityToggles() = runTest {
        val vm = vm()
        assertTrue(vm.demo.value.debugPanelVisible)
        vm.setDebugPanelVisible(false)
        assertEquals(false, vm.demo.value.debugPanelVisible)
    }

    @Test fun timeLabelFormat() {
        assertEquals("0:00 / 0:55", formatDemoTime(0, 55_000))
        assertEquals("1:05 / 0:55", formatDemoTime(65_000, 55_000))
    }

    // --- 003 실차 신호 (US1) ---

    @Test fun defaultModeIsVehicle() = runTest {
        val vm = vehicleVm()
        runCurrent()
        assertEquals(SignalMode.VEHICLE, vm.demo.value.mode)
    }

    @Test fun vehicleSignalsReachTheEngine() = runTest {
        val source = FakeVehicleSource()
        val vm = vehicleVm(source)
        vm.onForeground()
        runCurrent()
        assertEquals(MotionTier.STATIC, vm.output.value.tier) // 제한을 모르면 정적
        source.flow.tryEmit(Restriction(0, RestrictionState.NONE))
        runCurrent()
        assertEquals(MotionTier.FULL, vm.output.value.tier)
        source.flow.tryEmit(Restriction(100, RestrictionState.ACTIVE))
        runCurrent()
        assertEquals(MotionTier.STATIC, vm.output.value.tier)
    }

    @Test fun manualRestrictionIsIgnoredInVehicleMode() = runTest {
        val vm = vehicleVm()
        runCurrent()
        vm.setRestriction(RestrictionState.NONE)
        runCurrent()
        assertEquals(MotionTier.STATIC, vm.output.value.tier) // 실차 상태를 덮어쓰지 못한다
        assertNull(vm.demo.value.manualRestriction)
    }

    @Test fun togglePlayDoesNothingInVehicleMode() = runTest {
        val vm = vehicleVm()
        runCurrent()
        vm.togglePlay()
        runCurrent()
        assertEquals(PlaybackState.STOPPED, vm.demo.value.playback)
        assertEquals(0L, vm.demo.value.elapsedMs)
    }

    // --- 003 권한 결과 (US3) ---

    @Test fun speedPermissionResultRestartsTheVehicleSourceOnce() = runTest {
        val source = FakeVehicleSource()
        val vm = vehicleVm(source)
        vm.onForeground()
        runCurrent()
        assertEquals(1, source.started)
        vm.onSpeedPermissionResult(granted = true)
        runCurrent()
        assertEquals(1, source.stopped)
        assertEquals(2, source.started)
    }

    @Test fun deniedPermissionAlsoRestartsSoTheSourceReportsTheReason() = runTest {
        val source = FakeVehicleSource()
        val vm = vehicleVm(source)
        vm.onForeground()
        runCurrent()
        vm.onSpeedPermissionResult(granted = false)
        runCurrent()
        assertEquals(2, source.started)
    }

    @Test fun speedPermissionResultDoesNothingInDemoMode() = runTest {
        val source = FakeVehicleSource()
        val vm = DemoViewModel(vehicleSource = source, initialMode = SignalMode.DEMO)
        runCurrent()
        vm.onSpeedPermissionResult(granted = true)
        runCurrent()
        assertEquals(0, source.started)
        assertEquals(0, source.stopped)
    }

    // --- 003 모드 전환 (US4) ---

    @Test fun switchingToDemoRestoresPlaybackAndManualRestriction() = runTest {
        val vm = vehicleVm()
        runCurrent()
        vm.setMode(SignalMode.DEMO)
        runCurrent()
        assertEquals(SignalMode.DEMO, vm.demo.value.mode)
        vm.setRestriction(RestrictionState.NONE)
        runCurrent()
        assertEquals(MotionTier.FULL, vm.output.value.tier)
        vm.togglePlay()
        runCurrent()
        assertEquals(PlaybackState.PLAYING, vm.demo.value.playback)
    }

    @Test fun switchingBackToVehicleResetsDemoStateAndEngine() = runTest {
        val source = FakeVehicleSource()
        val vm = DemoViewModel(vehicleSource = source, pacing = Pacing.IMMEDIATE, debugAvailable = true, initialMode = SignalMode.DEMO)
        vm.onForeground() // 화면에 보이는 중에 모드를 바꾼다
        runCurrent()
        vm.setRestriction(RestrictionState.NONE)
        vm.togglePlay()
        advanceTimeBy(500)
        runCurrent()
        vm.setMode(SignalMode.VEHICLE)
        runCurrent()
        assertEquals(SignalMode.VEHICLE, vm.demo.value.mode)
        assertEquals(PlaybackState.STOPPED, vm.demo.value.playback)
        assertEquals(0L, vm.demo.value.elapsedMs)
        assertNull(vm.demo.value.manualRestriction)
        // 데모가 남긴 엔진 상태와 신호가 없다: 처음 상태(정적, 편안)에서 실차 값부터 시작한다.
        assertEquals(MotionTier.STATIC, vm.output.value.tier)
        assertEquals(Mood.IDLE, vm.output.value.mood)
        advanceTimeBy(60_000) // 목업이 정지됐으므로 시간이 가도 데모 신호가 들어오지 않는다
        runCurrent()
        assertEquals(MotionTier.STATIC, vm.output.value.tier)
        assertEquals(Mood.IDLE, vm.output.value.mood)
    }

    @Test fun alternatingModesTenTimesNeverMixesSources() = runTest {
        val source = FakeVehicleSource()
        val vm = vehicleVm(source)
        vm.onForeground()
        runCurrent()
        repeat(10) {
            vm.setMode(SignalMode.DEMO)
            runCurrent()
            // 데모 모드에서 실차 소스가 신호를 내도 엔진에 들어오지 않는다.
            source.flow.tryEmit(Restriction(0, RestrictionState.NONE))
            runCurrent()
            assertEquals(MotionTier.STATIC, vm.output.value.tier)
            vm.setMode(SignalMode.VEHICLE)
            runCurrent()
            assertEquals(MotionTier.STATIC, vm.output.value.tier) // 엔진은 새 세션
            source.flow.tryEmit(Restriction(0, RestrictionState.NONE))
            runCurrent()
            assertEquals(MotionTier.FULL, vm.output.value.tier)
        }
    }

    @Test fun diagnosticsExistOnlyInVehicleMode() = runTest {
        val diagnostics = MutableStateFlow(SignalDiagnostics.INITIAL)
        val vm = vehicleVm(diagnostics = diagnostics)
        runCurrent()
        assertEquals(SignalDiagnostics.INITIAL, vm.demo.value.diagnostics)
        val connected = SignalDiagnostics.INITIAL.copy(link = CarLinkState.CONNECTED)
        diagnostics.value = connected
        runCurrent()
        assertEquals(connected, vm.demo.value.diagnostics) // 갱신이 따라온다
        vm.setMode(SignalMode.DEMO)
        runCurrent()
        assertNull(vm.demo.value.diagnostics)
        diagnostics.value = SignalDiagnostics.INITIAL // 데모 모드에서는 값이 바뀌어도 보이지 않는다
        runCurrent()
        assertNull(vm.demo.value.diagnostics)
        vm.setMode(SignalMode.VEHICLE)
        runCurrent()
        assertEquals(SignalDiagnostics.INITIAL, vm.demo.value.diagnostics)
    }

    @Test fun settingTheSameModeDoesNothing() = runTest {
        val source = FakeVehicleSource()
        val vm = vehicleVm(source)
        vm.onForeground()
        runCurrent()
        vm.setMode(SignalMode.VEHICLE)
        runCurrent()
        assertEquals(1, source.started)
        assertEquals(0, source.stopped)
    }

    // --- 003 수명주기 (US5) ---

    @Test fun vehicleSourceDoesNotStartUntilTheScreenIsVisible() = runTest {
        val source = FakeVehicleSource()
        vehicleVm(source)
        runCurrent()
        assertEquals(0, source.started)
    }

    @Test fun onForegroundStartsOnceAndIsIdempotent() = runTest {
        val source = FakeVehicleSource()
        val vm = vehicleVm(source)
        vm.onForeground()
        vm.onForeground() // 화면 회전으로 다시 불려도 중복 시작이 없다
        runCurrent()
        assertEquals(1, source.started)
        assertEquals(0, source.stopped)
    }

    @Test fun onBackgroundStopsReceivingAndKeepsTheEngineFromProcessingMoreSignals() = runTest {
        val source = FakeVehicleSource()
        val vm = vehicleVm(source)
        vm.onForeground()
        runCurrent()
        source.flow.tryEmit(Restriction(0, RestrictionState.NONE))
        runCurrent()
        assertEquals(MotionTier.FULL, vm.output.value.tier)
        vm.onBackground()
        runCurrent()
        assertEquals(1, source.stopped)
        source.flow.tryEmit(Restriction(100, RestrictionState.ACTIVE)) // 보이지 않는 동안 온 신호는 처리되지 않는다
        runCurrent()
        assertEquals(MotionTier.FULL, vm.output.value.tier)
    }

    @Test fun returningToTheForegroundStartsAFreshSession() = runTest {
        val source = FakeVehicleSource()
        val vm = vehicleVm(source)
        vm.onForeground()
        runCurrent()
        source.flow.tryEmit(Restriction(0, RestrictionState.NONE))
        runCurrent()
        vm.onBackground()
        vm.onForeground()
        runCurrent()
        assertEquals(2, source.started)
        assertEquals(MotionTier.STATIC, vm.output.value.tier) // 새 세션: 제한을 다시 읽기 전까지 정적
        source.flow.tryEmit(Restriction(0, RestrictionState.NONE)) // 다시 읽은 현재 값
        runCurrent()
        assertEquals(MotionTier.FULL, vm.output.value.tier)
    }

    @Test fun twentyForegroundBackgroundCyclesLeaveNothingOpen() = runTest {
        val source = FakeVehicleSource()
        val vm = vehicleVm(source)
        repeat(20) {
            vm.onForeground()
            runCurrent()
            vm.onBackground()
            runCurrent()
        }
        assertEquals(20, source.started)
        assertEquals(20, source.stopped)
    }

    @Test fun backgroundSpeedPermissionResultDoesNotStartTheSource() = runTest {
        val source = FakeVehicleSource()
        val vm = vehicleVm(source)
        runCurrent()
        vm.onSpeedPermissionResult(granted = true) // 보이지 않는 동안에는 시작하지 않는다
        runCurrent()
        assertEquals(0, source.started)
    }

    @Test fun switchingToVehicleInTheBackgroundDoesNotStartTheSource() = runTest {
        val source = FakeVehicleSource()
        val vm = DemoViewModel(vehicleSource = source, initialMode = SignalMode.DEMO)
        runCurrent()
        vm.setMode(SignalMode.VEHICLE)
        runCurrent()
        assertEquals(0, source.started)
        vm.onForeground()
        runCurrent()
        assertEquals(1, source.started)
    }

    @Test fun demoModeIgnoresLifecycle() = runTest {
        val source = FakeVehicleSource()
        val vm = DemoViewModel(vehicleSource = source, initialMode = SignalMode.DEMO, debugAvailable = true)
        runCurrent()
        vm.togglePlay()
        runCurrent()
        vm.onBackground()
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(PlaybackState.PLAYING, vm.demo.value.playback) // 데모 재생과 엔진은 그대로
        vm.onForeground()
        runCurrent()
        assertEquals(PlaybackState.PLAYING, vm.demo.value.playback)
        assertEquals(0, source.started)
        assertEquals(0, source.stopped)
    }
}
