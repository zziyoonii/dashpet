package com.example.dashpet.ui.demo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dashpet.engine.DrivingEngine
import com.example.dashpet.engine.DrivingEvent
import com.example.dashpet.engine.PetOutput
import com.example.dashpet.signal.MockSignalSource
import com.example.dashpet.signal.Pacing
import com.example.dashpet.signal.RawSignal
import com.example.dashpet.signal.RestrictionState
import com.example.dashpet.signal.VehicleSignalSource
import com.example.dashpet.signal.scenario.DemoScenarios
import com.example.dashpet.signal.scenario.Scenario
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PlaybackState { STOPPED, PLAYING }

/** 데모 화면 상태(data-model.md DemoState). */
data class DemoState(
    val playback: PlaybackState = PlaybackState.STOPPED,
    val elapsedMs: Long = 0L,
    val manualRestriction: RestrictionState? = null,
    val debugAvailable: Boolean = false,
    val debugPanelVisible: Boolean = true,
)

/** `start()`/`stop()` 이 아무것도 하지 않고 신호만 위임한다. 재생 시작/정지는 ViewModel 이 원본 소스로 직접 한다. */
private class PassiveSource(private val delegate: VehicleSignalSource) : VehicleSignalSource {
    override val signals: Flow<RawSignal> get() = delegate.signals
    override fun start() = Unit
    override fun stop() = Unit
}

/**
 * 데모 ViewModel. 엔진을 소유하며 화면 회전에도 재생이 이어진다. 엔진은 항상 켜 두고(수동 제한 상태 주입이 항상 처리됨)
 * 재생만 시작/정지한다. 새 세션은 `engine.stop()` 후 `engine.start()`. 수동 제한은 `injectRestriction` 으로만 바꾼다.
 * 시간은 `delay` 로만 센다(벽시계 미사용).
 */
class DemoViewModel(
    scenario: Scenario = DemoScenarios.demoCommute,
    pacing: Pacing = Pacing.REAL_TIME,
    debugAvailable: Boolean = false,
) : ViewModel() {
    private val mock = MockSignalSource(scenario, pacing)

    /** 컨트롤러 연결용(`PetController.collectFrom(engine)`). 메인 스레드에서만 쓴다. */
    val engine = DrivingEngine(PassiveSource(mock), viewModelScope)

    /** 시나리오 총 길이(마지막 신호 시각 + 틱 꼬리). */
    val totalMs: Long = (scenario.expand().lastOrNull()?.timestampMs ?: 0L) + MockSignalSource.DEFAULT_TAIL_MS

    val output: StateFlow<PetOutput> get() = engine.output
    val events: SharedFlow<DrivingEvent> get() = engine.events

    private val _demo = MutableStateFlow(DemoState(debugAvailable = debugAvailable))
    val demo: StateFlow<DemoState> = _demo.asStateFlow()

    private var timer: Job? = null

    init {
        engine.start()
    }

    fun togglePlay() {
        if (_demo.value.playback == PlaybackState.PLAYING) stop() else play()
    }

    private fun play() {
        newSession()
        _demo.update { it.copy(playback = PlaybackState.PLAYING) }
        mock.start()
        timer = viewModelScope.launch {
            while (_demo.value.elapsedMs < totalMs) {
                delay(TIMER_STEP_MS)
                _demo.update { it.copy(elapsedMs = (it.elapsedMs + TIMER_STEP_MS).coerceAtMost(totalMs)) }
            }
            mock.stop()
            _demo.update { it.copy(playback = PlaybackState.STOPPED) }
        }
    }

    /** 재생을 멈추고 펫을 편안 상태로 되돌린다(새 세션). */
    fun stop() {
        newSession()
    }

    private fun newSession() {
        timer?.cancel()
        timer = null
        mock.stop()
        engine.stop()
        engine.start()
        _demo.update { it.copy(playback = PlaybackState.STOPPED, elapsedMs = 0L, manualRestriction = null) }
    }

    fun setRestriction(state: RestrictionState) {
        if (!_demo.value.debugAvailable) return
        engine.injectRestriction(state)
        _demo.update { it.copy(manualRestriction = state) }
    }

    fun setDebugPanelVisible(visible: Boolean) {
        _demo.update { it.copy(debugPanelVisible = visible) }
    }

    override fun onCleared() {
        mock.stop()
        engine.stop()
    }

    private companion object {
        const val TIMER_STEP_MS = 100L
    }
}

/** `m:ss / m:ss`. */
fun formatDemoTime(elapsedMs: Long, totalMs: Long): String = "${mmss(elapsedMs)} / ${mmss(totalMs)}"

private fun mmss(ms: Long): String {
    val s = ms.coerceAtLeast(0L) / 1000
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}
