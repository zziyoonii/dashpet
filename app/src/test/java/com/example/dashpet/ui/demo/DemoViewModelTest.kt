package com.example.dashpet.ui.demo

import com.example.dashpet.engine.MotionTier
import com.example.dashpet.engine.Mood
import com.example.dashpet.signal.Pacing
import com.example.dashpet.signal.RestrictionState
import com.example.dashpet.signal.scenario.DemoScenarios
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

@OptIn(ExperimentalCoroutinesApi::class)
class DemoViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm(pacing: Pacing = Pacing.REAL_TIME, debug: Boolean = true) =
        DemoViewModel(DemoScenarios.demoCommute, pacing, debug)

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
}
