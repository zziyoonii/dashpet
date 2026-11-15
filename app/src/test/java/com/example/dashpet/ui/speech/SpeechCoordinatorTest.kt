package com.example.dashpet.ui.speech

import com.example.dashpet.engine.DrivingEvent
import com.example.dashpet.engine.EventType
import com.example.dashpet.engine.Mood
import com.example.dashpet.engine.MotionTier
import com.example.dashpet.engine.PetOutput
import com.example.dashpet.engine.speech.SpeechConfig
import com.example.dashpet.signal.TurnState
import com.example.dashpet.signal.voice.FailReason
import com.example.dashpet.signal.voice.NoOpSpeechOutput
import com.example.dashpet.signal.voice.SpeechOutput
import com.example.dashpet.signal.voice.SpeechState
import com.example.dashpet.signal.voice.VoiceAvailability
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SpeechCoordinatorTest {
    private class FakeOutput : SpeechOutput {
        val ch = Channel<SpeechState>(Channel.UNLIMITED)
        override val states: Flow<SpeechState> = ch.receiveAsFlow()
        override val availability: StateFlow<VoiceAvailability> = MutableStateFlow(VoiceAvailability.READY)
        val ops = mutableListOf<String>()
        var released = false
        override fun speak(id: Long, text: String) { ops += "speak:$text" }
        override fun stop() { ops += "stop" }
        override fun release() { released = true; ops += "release" }
        fun end(id: Long) { ch.trySend(SpeechState.Finished(id)) }
    }

    private class Rig(val scope: TestScope, val settings: VoiceSettings = MemoryVoiceSettings(true), noop: Boolean = false) {
        val events = MutableSharedFlow<DrivingEvent>(extraBufferCapacity = 64)
        val output = MutableStateFlow(out(MotionTier.FULL))
        val made = mutableListOf<SpeechOutput>()
        val coordinator = SpeechCoordinator(
            scope = scope.backgroundScope,
            events = events,
            output = output,
            speechFactory = { _ -> (if (noop) NoOpSpeechOutput() else FakeOutput()).also { made += it } },
            settings = settings,
            config = SpeechConfig(),
            clock = { scope.currentTime },
        )
        val fake get() = made.last() as FakeOutput

        init { coordinator.start(); coordinator.onForeground(); scope.runCurrent() }

        fun event(type: EventType) { events.tryEmit(DrivingEvent(type, scope.currentTime)); scope.runCurrent() }
        fun tier(t: MotionTier) { output.value = out(t); scope.runCurrent() }
        fun idFromLast(): Long = (made.size).toLong() // 사용하지 않음
    }

    private companion object {
        fun out(t: MotionTier) = PetOutput(Mood.IDLE, 0f, null, t, TurnState.OFF, 0)
    }

    private fun TestScope.rig(settings: VoiceSettings = MemoryVoiceSettings(true), noop: Boolean = false) = Rig(this, settings, noop)

    @Test fun debugSpeakFollowsCurrentTier() = runTest {
        val r = rig()
        r.coordinator.debugSpeak(EventType.SEATBELT_OK)
        runCurrent()
        assertEquals("안전벨트를 확인했어요.", r.coordinator.bubble.value?.text) // FULL: 말풍선 + 음성
        r.tier(MotionTier.STATIC)
        assertNull(r.coordinator.bubble.value)
        r.coordinator.debugSpeak(EventType.TRIP_START)
        runCurrent()
        assertNull(r.coordinator.bubble.value) // 제한 중에는 글자 없음
    }

    @Test fun debugSpeakIgnoredInBackground() = runTest {
        val r = rig()
        r.coordinator.onBackground()
        r.coordinator.debugSpeak(EventType.SEATBELT_OK)
        runCurrent()
        assertNull(r.coordinator.bubble.value)
    }

    @Test fun fullShowsBubbleAndSpeaksSameText() = runTest {
        val r = rig()
        r.event(EventType.HARD_ACCEL)
        assertEquals("출발이 조금 급했어요. 부드럽게 가요.", r.coordinator.bubble.value?.text)
        assertEquals(EventType.HARD_ACCEL, r.coordinator.bubble.value?.eventType)
        assertEquals(listOf("speak:출발이 조금 급했어요. 부드럽게 가요."), r.fake.ops) // 말풍선과 음성이 같은 문장(FR-003)
    }

    @Test fun bubbleDisappearsAfterDuration() = runTest {
        val r = rig()
        r.event(EventType.HARD_ACCEL)
        advanceTimeBy(3_999); runCurrent()
        assertNotNull(r.coordinator.bubble.value)
        advanceTimeBy(2); runCurrent()
        assertNull(r.coordinator.bubble.value)
    }

    @Test fun newSpeechReplacesBubbleImmediately() = runTest {
        val r = rig()
        r.event(EventType.HARD_ACCEL)
        r.fake.end(1); runCurrent()
        r.event(EventType.HARD_BRAKE)
        assertEquals(EventType.HARD_BRAKE, r.coordinator.bubble.value?.eventType)
    }

    @Test fun staticTierNeverShowsBubbleButSpeaksShortText() = runTest {
        val r = rig()
        r.tier(MotionTier.STATIC)
        r.event(EventType.HARD_ACCEL)
        assertNull(r.coordinator.bubble.value)
        assertEquals(listOf("speak:부드럽게 가요."), r.fake.ops)
    }

    @Test fun bubbleClearsImmediatelyWhenRestrictionBegins() = runTest {
        val r = rig()
        r.event(EventType.HARD_ACCEL)
        assertNotNull(r.coordinator.bubble.value)
        val t0 = currentTime
        r.tier(MotionTier.STATIC)
        assertNull(r.coordinator.bubble.value)
        assertTrue(currentTime - t0 <= 1_000)
        r.tier(MotionTier.FULL)
        assertNull(r.coordinator.bubble.value) // 되살리지 않는다
    }

    @Test fun preemptionStopsThenSpeaks() = runTest {
        val r = rig()
        r.event(EventType.GOOD_LANE_CHANGE)
        r.event(EventType.COLLISION_RISK)
        assertEquals(listOf("speak:차선 변경이 깔끔했어요.", "stop", "speak:앞차와 가까워요. 거리를 두세요."), r.fake.ops)
        assertEquals(EventType.COLLISION_RISK, r.coordinator.bubble.value?.eventType)
    }

    @Test fun eventsWhileBackgroundAreIgnoredAndReleasesOutput() = runTest {
        val r = rig()
        val first = r.fake
        r.coordinator.onBackground(); runCurrent()
        assertTrue(first.released)
        assertNull(r.coordinator.bubble.value)
        r.event(EventType.HARD_ACCEL)
        assertNull(r.coordinator.bubble.value)
        assertEquals(1, r.made.size)
    }

    @Test fun backgroundForegroundCyclesCreateAndReleaseEveryOutput() = runTest {
        val r = rig()
        repeat(20) {
            r.coordinator.onBackground(); runCurrent()
            r.coordinator.onForeground(); runCurrent()
        }
        assertEquals(21, r.made.size)
        assertTrue(r.made.dropLast(1).all { (it as FakeOutput).released })
        r.coordinator.onForeground() // 이미 보이는 중이면 새로 만들지 않는다
        assertEquals(21, r.made.size)
    }

    @Test fun voiceOffShowsBubbleOnlyAndPersists() = runTest {
        val settings = MemoryVoiceSettings(true)
        val r = rig(settings)
        assertTrue(r.coordinator.setVoiceEnabled(false))
        assertFalse(settings.voiceEnabled)
        r.event(EventType.HARD_ACCEL)
        assertNotNull(r.coordinator.bubble.value)
        assertEquals(emptyList<String>(), r.fake.ops - "stop")
        // 새 코디네이터(앱 재시작)도 꺼진 상태
        val r2 = rig(settings)
        assertFalse(r2.coordinator.voiceEnabled.value)
    }

    @Test fun voiceOffDoesNotBlockNextSpeech() = runTest {
        val r = rig(MemoryVoiceSettings(false))
        r.event(EventType.HARD_ACCEL)
        r.event(EventType.COLLISION_RISK) // 말하는 중으로 남아 있으면 대기열로 가 버린다
        assertEquals(EventType.COLLISION_RISK, r.coordinator.bubble.value?.eventType)
    }

    @Test fun voiceSettingChangeIgnoredWhileRestricted() = runTest {
        val settings = MemoryVoiceSettings(true)
        val r = rig(settings)
        r.tier(MotionTier.STATIC)
        assertFalse(r.coordinator.setVoiceEnabled(false))
        assertTrue(settings.voiceEnabled)
        assertTrue(r.coordinator.voiceEnabled.value)
    }

    @Test fun turningVoiceOffStopsCurrentSpeech() = runTest {
        val r = rig()
        r.event(EventType.HARD_ACCEL)
        r.coordinator.setVoiceEnabled(false); runCurrent()
        assertTrue("stop" in r.fake.ops)
    }

    @Test fun unavailableVoiceNeverBreaksAndKeepsBubble() = runTest {
        val r = rig(noop = true)
        repeat(100) { i ->
            r.event(EventType.entries[i % EventType.entries.size])
            advanceTimeBy(20_000); runCurrent()
        }
        r.event(EventType.HARD_ACCEL)
        assertNotNull(r.coordinator.bubble.value)
    }

    @Test fun resetClearsBubbleAndPending() = runTest {
        val r = rig()
        r.event(EventType.HARD_ACCEL)
        r.event(EventType.HARD_BRAKE) // 대기
        r.coordinator.reset(); runCurrent()
        assertNull(r.coordinator.bubble.value)
        assertTrue("stop" in r.fake.ops)
        // 이전 말의 종료 보고가 와도 대기 중이던 말을 꺼내지 않는다
        r.fake.end(1); runCurrent()
        assertNull(r.coordinator.bubble.value)
        assertEquals(1, r.fake.ops.count { it.startsWith("speak:") })
    }

    @Test fun failedStateEndsSpeech() = runTest {
        val r = rig()
        r.event(EventType.HARD_ACCEL)
        r.fake.ch.trySend(SpeechState.Failed(1, FailReason.NO_FOCUS)); runCurrent()
        r.event(EventType.COLLISION_RISK) // 말하는 중이 아니므로 바로 말한다(선점 stop 없음)
        assertFalse("stop" in r.fake.ops)
    }
}
