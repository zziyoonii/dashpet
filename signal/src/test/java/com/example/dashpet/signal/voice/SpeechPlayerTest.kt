package com.example.dashpet.signal.voice

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SpeechPlayerTest {
    private class Rig(scope: TestScope, grant: Boolean = true, avail: VoiceAvailability = VoiceAvailability.READY) {
        val order = mutableListOf<String>()
        val focus = FakeAudioFocusGateway(grant)
        val synth = FakeSynthesizer(avail)
        val states = mutableListOf<SpeechState>()
        val player = SpeechPlayer(synth, focus, FakeSilence(order), scope.backgroundScope, preSilenceMs = 180, watchdogMs = 8_000)

        // 구독을 먼저 건다(첫 상태를 놓치지 않게)
        init { scope.backgroundScope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { player.states.collect { states += it } } }
    }

    /** `advanceUntilIdle` 은 백그라운드 작업만 남으면 멈추므로 시간을 직접 흘린다. */
    private fun TestScope.settle(ms: Long = 200) { advanceTimeBy(ms); runCurrent() }

    private fun TestScope.rig(grant: Boolean = true, avail: VoiceAvailability = VoiceAvailability.READY) = Rig(this, grant, avail)

    @Test fun orderIsFocusSilenceSpeak() = runTest {
        val r = rig()
        r.player.speak(1, "안녕")
        settle()
        assertEquals(1, r.focus.requestCount)
        assertEquals(listOf("silence:180"), r.order)
        assertEquals(listOf("speak:안녕"), r.synth.log)
        assertTrue(currentTime >= 180) // 무음이 지난 뒤에 말한다
    }

    @Test fun speechStartsOnlyAfterSilence() = runTest {
        val r = rig()
        r.player.speak(1, "a")
        advanceTimeBy(100)
        assertEquals(emptyList<String>(), r.synth.log)
        advanceTimeBy(100)
        assertEquals(listOf("speak:a"), r.synth.log)
    }

    @Test fun finishedAbandonsOnce() = runTest {
        val r = rig()
        r.player.speak(1, "a"); settle()
        r.synth.emit(SynthEvent.Started); settle()
        r.synth.emit(SynthEvent.Done); settle()
        assertEquals(listOf<SpeechState>(SpeechState.Started(1), SpeechState.Finished(1)), r.states)
        assertEquals(1, r.focus.abandonCount)
        assertEquals(r.focus.requestCount, r.focus.abandonCount)
    }

    @Test fun focusDeniedFailsWithoutSpeaking() = runTest {
        val r = rig(grant = false)
        r.player.speak(1, "a"); settle()
        assertEquals(listOf<SpeechState>(SpeechState.Failed(1, FailReason.NO_FOCUS)), r.states)
        assertEquals(emptyList<String>(), r.synth.log)
        assertEquals(r.focus.requestCount, r.focus.abandonCount)
    }

    @Test fun everyLossStopsWithinHalfSecondAndDoesNotResume() = runTest {
        listOf(FocusChange.LOSS, FocusChange.LOSS_TRANSIENT, FocusChange.LOSS_TRANSIENT_CAN_DUCK).forEach { change ->
            val r = rig()
            r.player.speak(1, "a"); settle()
            val t0 = currentTime
            r.focus.emit(change); settle()
            assertTrue("$change", currentTime - t0 <= 500)
            assertEquals("$change", listOf<SpeechState>(SpeechState.Stopped(1)), r.states)
            assertTrue("$change", "stop" in r.synth.log)
            assertEquals("$change", 1, r.focus.abandonCount)
            // 포커스가 돌아와도 다시 말하지 않는다
            r.focus.emit(FocusChange.GAIN); settle()
            assertEquals("$change", listOf("speak:a", "stop"), r.synth.log)
        }
    }

    @Test fun lossDuringSilenceCancelsBeforeSpeaking() = runTest {
        val r = rig()
        r.player.speak(1, "a"); advanceTimeBy(50)
        r.focus.emit(FocusChange.LOSS_TRANSIENT); settle()
        assertEquals(emptyList<String>(), r.synth.log)
        assertEquals(listOf<SpeechState>(SpeechState.Stopped(1)), r.states)
        assertEquals(1, r.focus.abandonCount)
    }

    @Test fun gainWhenIdleIsIgnored() = runTest {
        val r = rig()
        r.focus.emit(FocusChange.GAIN); settle()
        assertEquals(emptyList<SpeechState>(), r.states)
        assertEquals(0, r.focus.abandonCount)
    }

    @Test fun engineErrorFinishesAndAbandons() = runTest {
        val r = rig()
        r.player.speak(1, "a"); settle()
        r.synth.emit(SynthEvent.Error); settle()
        assertEquals(listOf<SpeechState>(SpeechState.Failed(1, FailReason.ENGINE_ERROR)), r.states)
        assertEquals(1, r.focus.abandonCount)
    }

    @Test fun watchdogCleansUp() = runTest {
        val r = rig()
        r.player.speak(1, "a"); settle() // 합성기가 아무 콜백도 주지 않는다
        advanceTimeBy(8_001); settle()
        assertEquals(listOf<SpeechState>(SpeechState.Failed(1, FailReason.TIMEOUT)), r.states)
        assertEquals(1, r.focus.abandonCount)
        assertTrue("stop" in r.synth.log)
    }

    @Test fun busyWhileSpeaking() = runTest {
        val r = rig()
        r.player.speak(1, "a"); settle()
        r.player.speak(2, "b"); settle()
        assertEquals(listOf<SpeechState>(SpeechState.Failed(2, FailReason.BUSY)), r.states)
        assertEquals(1, r.focus.requestCount) // 첫 말의 상태가 바뀌지 않는다
        assertEquals(0, r.focus.abandonCount)
    }

    @Test fun unavailableFailsOnceWithoutThrowingOrFocus() = runTest {
        val r = rig(avail = VoiceAvailability.UNAVAILABLE)
        repeat(100) { r.player.speak(it.toLong(), "a") }
        settle()
        assertEquals(100, r.states.size)
        assertTrue(r.states.all { it is SpeechState.Failed })
        assertEquals(0, r.focus.requestCount)
    }

    @Test fun explicitStopStopsAndAbandons() = runTest {
        val r = rig()
        r.player.speak(1, "a"); settle()
        r.player.stop(); settle()
        assertEquals(listOf<SpeechState>(SpeechState.Stopped(1)), r.states)
        assertEquals(1, r.focus.abandonCount)
    }

    @Test fun repeatedCyclesKeepCountsEqual() = runTest {
        val r = rig()
        repeat(20) { i ->
            r.player.speak(i.toLong(), "a"); settle()
            if (i % 2 == 0) r.synth.emit(SynthEvent.Done) else r.player.stop()
            settle()
        }
        assertEquals(20, r.focus.requestCount)
        assertEquals(r.focus.requestCount, r.focus.abandonCount)
    }

    @Test fun releaseStopsAndReleasesSynth() = runTest {
        val r = rig()
        r.player.speak(1, "a"); settle()
        r.player.release(); settle()
        assertTrue(r.synth.released)
        assertEquals(r.focus.requestCount, r.focus.abandonCount)
        r.player.speak(2, "b"); settle()
        assertEquals(1, r.focus.requestCount) // 해제 뒤에는 쓰지 않는다
    }
}
