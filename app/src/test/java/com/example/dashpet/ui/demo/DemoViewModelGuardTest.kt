package com.example.dashpet.ui.demo

import com.example.dashpet.engine.MotionTier
import com.example.dashpet.signal.ParkedState
import com.example.dashpet.signal.Pacing
import com.example.dashpet.signal.Restriction
import com.example.dashpet.signal.RestrictionState
import com.example.dashpet.signal.Speed
import com.example.dashpet.signal.scenario.DemoScenarios
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 제출용(release) 빌드의 데모 모드(specs/005): 데모는 실차의 주행 제한을 보지 않으므로, 정차 중에 데모로 바꾼 뒤 실차가 정차 상태를
 * 벗어나면 자동으로 실차 모드로 돌아간다(헌장 I). 데모에 들어가면 정차 기준 상태에서 시작해 컨트롤이 계속 보인다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DemoViewModelGuardTest {
    @Before fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private class Rig(val source: FakeVehicleSource = FakeVehicleSource(), debug: Boolean = false) {
        val vm = DemoViewModel(
            vehicleSource = source,
            scenario = DemoScenarios.demoCommute,
            pacing = Pacing.REAL_TIME,
            debugAvailable = debug,
            initialMode = SignalMode.VEHICLE,
        )
    }

    private fun kotlinx.coroutines.test.TestScope.parkedVehicle(rig: Rig) {
        runCurrent()
        rig.vm.onForeground()
        runCurrent()
        rig.source.flow.tryEmit(Restriction(0, RestrictionState.NONE))
        rig.source.flow.tryEmit(ParkedState(0, true))
        rig.source.flow.tryEmit(Speed(0, 0f))
        runCurrent()
    }

    @Test fun enteringDemoStartsFromParkedBaselineSoControlsStayAvailable() = runTest {
        val rig = Rig()
        parkedVehicle(rig)
        assertEquals(MotionTier.FULL, rig.vm.output.value.tier)
        rig.vm.setMode(SignalMode.DEMO); runCurrent()
        assertEquals(MotionTier.FULL, rig.vm.output.value.tier) // 재생 버튼이 보이는 상태
        rig.vm.togglePlay(); runCurrent()
        rig.vm.togglePlay(); runCurrent() // 정지 후에도 다시 재생할 수 있어야 한다
        assertEquals(MotionTier.FULL, rig.vm.output.value.tier)
    }

    @Test fun realRestrictionEndsDemo() = runTest {
        val rig = Rig()
        parkedVehicle(rig)
        rig.vm.setMode(SignalMode.DEMO); runCurrent()
        rig.source.flow.tryEmit(Restriction(10, RestrictionState.ACTIVE)); runCurrent()
        assertEquals(SignalMode.VEHICLE, rig.vm.demo.value.mode)
    }

    @Test fun realMovementEndsDemo() = runTest {
        val speed = Rig(); parkedVehicle(speed)
        speed.vm.setMode(SignalMode.DEMO); runCurrent()
        speed.source.flow.tryEmit(Speed(10, 2f)); runCurrent()
        assertEquals(SignalMode.VEHICLE, speed.vm.demo.value.mode)

        val gear = Rig(); parkedVehicle(gear)
        gear.vm.setMode(SignalMode.DEMO); runCurrent()
        gear.source.flow.tryEmit(ParkedState(10, false)); runCurrent()
        assertEquals(SignalMode.VEHICLE, gear.vm.demo.value.mode)
    }

    @Test fun parkedVehicleKeepsDemo() = runTest {
        val rig = Rig()
        parkedVehicle(rig)
        rig.vm.setMode(SignalMode.DEMO); runCurrent()
        rig.source.flow.tryEmit(Restriction(10, RestrictionState.NONE))
        rig.source.flow.tryEmit(ParkedState(10, true))
        rig.source.flow.tryEmit(Speed(10, 0f)); runCurrent()
        assertEquals(SignalMode.DEMO, rig.vm.demo.value.mode)
    }

    @Test fun unknownRestrictionEndsDemoToo() = runTest {
        val rig = Rig()
        parkedVehicle(rig)
        rig.vm.setMode(SignalMode.DEMO); runCurrent()
        rig.source.flow.tryEmit(Restriction(10, RestrictionState.UNKNOWN)); runCurrent()
        assertEquals(SignalMode.VEHICLE, rig.vm.demo.value.mode)
    }

    @Test fun debugBuildHasNoGuard() = runTest {
        val rig = Rig(debug = true)
        parkedVehicle(rig)
        rig.vm.setMode(SignalMode.DEMO); runCurrent()
        rig.source.flow.tryEmit(Restriction(10, RestrictionState.ACTIVE)); runCurrent()
        assertEquals(SignalMode.DEMO, rig.vm.demo.value.mode) // 개발용 데모는 제한 상태를 직접 바꿔 가며 쓴다
    }

    @Test fun backgroundStopsTheGuardSource() = runTest {
        val rig = Rig()
        parkedVehicle(rig)
        rig.vm.setMode(SignalMode.DEMO); runCurrent()
        val stoppedBefore = rig.source.stopped
        rig.vm.onBackground(); runCurrent()
        assertTrue("백그라운드에서는 차량 연결을 닫는다", rig.source.stopped > stoppedBefore)
        val startedBefore = rig.source.started
        rig.vm.onForeground(); runCurrent()
        assertTrue("다시 보이면 가드를 다시 시작한다", rig.source.started > startedBefore)
    }

    @Test fun demoPlaybackStillWorksAfterGuardStarts() = runTest {
        val rig = Rig()
        parkedVehicle(rig)
        rig.vm.setMode(SignalMode.DEMO); runCurrent()
        rig.vm.togglePlay(); runCurrent()
        advanceTimeBy(2_000); runCurrent()
        assertEquals(PlaybackState.PLAYING, rig.vm.demo.value.playback)
        assertEquals(SignalMode.DEMO, rig.vm.demo.value.mode)
    }
}
