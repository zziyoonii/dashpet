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
import com.example.dashpet.signal.SwitchableSignalSource
import com.example.dashpet.signal.VehicleSignalSource
import com.example.dashpet.signal.car.SignalDiagnostics
import com.example.dashpet.signal.voice.NoOpSpeechOutput
import com.example.dashpet.signal.voice.SpeechOutput
import com.example.dashpet.ui.speech.MemoryVoiceSettings
import com.example.dashpet.ui.speech.SpeechCoordinator
import com.example.dashpet.ui.speech.VoiceSettings
import kotlinx.coroutines.CoroutineScope
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

/** 신호를 어디서 받는지. 한 번에 하나만 활성이다(spec FR-015). */
enum class SignalMode { VEHICLE, DEMO }

/** 데모 화면 상태(data-model.md DemoState). */
data class DemoState(
    val mode: SignalMode = SignalMode.VEHICLE,
    /** 실차 신호 진단. 실차 모드에서만 값이 있고 데모 모드에서는 null. */
    val diagnostics: SignalDiagnostics? = null,
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
    /** 실차 신호 소스. 앱은 `CarSignalSource.create(context)` 를 넘긴다(`:app` 은 `android.car` 를 직접 보지 않는다). */
    private val vehicleSource: VehicleSignalSource,
    scenario: Scenario = DemoScenarios.demoCommute,
    pacing: Pacing = Pacing.REAL_TIME,
    debugAvailable: Boolean = false,
    initialMode: SignalMode = SignalMode.VEHICLE,
    /** 실차 소스의 진단 값(`CarSignalSource.diagnostics`). 없으면 진단을 보이지 않는다. */
    private val diagnostics: StateFlow<SignalDiagnostics>? = null,
    /** 모드 전환 같은 드문 사건만 한 줄씩 남긴다(앱은 `Log.i("CarSignal", it)` 를 넘긴다). 단위 테스트가 Android 없이 돌도록 주입받는다. */
    private val log: (String) -> Unit = {},
    /** 음성 출력을 만든다. 화면에 보일 때마다 새로 만들고 사라지면 해제한다. 앱은 `createPleosSpeechOutput` 을 넘긴다. */
    speechFactory: (CoroutineScope) -> SpeechOutput = { NoOpSpeechOutput() },
    voiceSettings: VoiceSettings = MemoryVoiceSettings(),
    speechLog: (String) -> Unit = {},
    clock: () -> Long = { System.nanoTime() / 1_000_000 },
) : ViewModel() {
    private val mock = MockSignalSource(scenario, pacing)
    private val demoSource = PassiveSource(mock)

    /** 엔진이 보는 단일 소스. 모드에 맞는 소스의 신호만 내보낸다. */
    private val switchable = SwitchableSignalSource(if (initialMode == SignalMode.VEHICLE) vehicleSource else demoSource)

    /** 컨트롤러 연결용(`PetController.collectFrom(engine)`). 메인 스레드에서만 쓴다. */
    val engine = DrivingEngine(switchable, viewModelScope)

    /** 시나리오 총 길이(마지막 신호 시각 + 틱 꼬리). */
    val totalMs: Long = (scenario.expand().lastOrNull()?.timestampMs ?: 0L) + MockSignalSource.DEFAULT_TAIL_MS

    /** 말풍선과 음성(specs/004). 엔진의 이벤트와 출력을 받아 쓰기만 한다. */
    val speech = SpeechCoordinator(
        scope = viewModelScope,
        events = engine.events,
        output = engine.output,
        speechFactory = speechFactory,
        settings = voiceSettings,
        clock = clock,
        log = speechLog,
    )

    val output: StateFlow<PetOutput> get() = engine.output
    val events: SharedFlow<DrivingEvent> get() = engine.events

    private val _demo = MutableStateFlow(
        DemoState(
            mode = initialMode,
            debugAvailable = debugAvailable,
            diagnostics = if (initialMode == SignalMode.VEHICLE) diagnostics?.value else null,
        ),
    )
    val demo: StateFlow<DemoState> = _demo.asStateFlow()

    private var timer: Job? = null

    /** 화면에 보이는 중인가(`onForeground`/`onBackground`). 실차 모드의 엔진은 이 동안에만 돈다. */
    private var foreground = false

    init {
        speech.start() // 이벤트 구독은 엔진 시작 전에 건다(구독 전 이벤트는 받지 못한다)
        // 데모 모드의 엔진은 항상 켜 둔다(수동 제한 주입이 항상 처리되도록). 실차 모드는 화면에 보일 때 시작한다.
        if (initialMode == SignalMode.DEMO) engine.start()
        // 진단은 실차 모드에서만 보인다. 데모 모드에서는 값이 바뀌어도 반영하지 않는다.
        diagnostics?.let { flow ->
            viewModelScope.launch {
                flow.collect { d ->
                    _demo.update { if (it.mode == SignalMode.VEHICLE) it.copy(diagnostics = d) else it }
                }
            }
        }
    }

    /**
     * 신호 모드를 바꾼다. 순서는 `engine.stop()` → 소스 선택 → `engine.start()`(새 세션, 엔진 상태 초기화)로, 이전 모드의
     * 신호가 새 모드에 섞이지 않는다. 데모 재생과 수동 제한은 초기화된다. 같은 모드면 아무 일도 하지 않는다.
     */
    fun setMode(mode: SignalMode) {
        if (_demo.value.mode == mode) return
        log("mode=$mode")
        timer?.cancel()
        timer = null
        mock.stop()
        engine.stop()
        speech.reset() // 이전 모드의 말풍선과 대기 중인 음성이 남지 않는다
        switchable.select(if (mode == SignalMode.VEHICLE) vehicleSource else demoSource)
        // 실차 모드는 화면에 보이는 동안에만 시작한다. 보이지 않는 중에 바꿨다면 다시 보일 때 시작한다.
        if (mode == SignalMode.DEMO || foreground) engine.start()
        _demo.update {
            it.copy(
                mode = mode,
                playback = PlaybackState.STOPPED,
                elapsedMs = 0L,
                manualRestriction = null,
                diagnostics = if (mode == SignalMode.VEHICLE) diagnostics?.value else null,
            )
        }
    }

    fun togglePlay() {
        if (_demo.value.mode != SignalMode.DEMO) return // 실차 모드에서는 재생이 없다
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
        if (_demo.value.mode != SignalMode.DEMO) return
        newSession()
    }

    private fun newSession() {
        timer?.cancel()
        timer = null
        mock.stop()
        engine.stop()
        speech.reset()
        engine.start()
        _demo.update { it.copy(playback = PlaybackState.STOPPED, elapsedMs = 0L, manualRestriction = null) }
    }

    fun setRestriction(state: RestrictionState) {
        if (!_demo.value.debugAvailable) return
        if (_demo.value.mode != SignalMode.DEMO) return // 실차 상태를 덮어쓰지 못한다(FR-016)
        engine.injectRestriction(state)
        _demo.update { it.copy(manualRestriction = state) }
    }

    /**
     * 화면에 보이기 시작함. 실차 모드면 신호 수신을 시작한다. 이미 수신 중이면 아무것도 하지 않는다(화면 회전으로
     * 다시 불려도 중복 시작이 없다). 시작은 새 세션이라 현재 속도, 기어, 제한 상태를 한 번씩 다시 읽는다. 데모 모드는 건드리지 않는다.
     */
    fun onForeground() {
        foreground = true
        speech.onForeground()
        if (_demo.value.mode == SignalMode.VEHICLE) engine.start()
    }

    /** 화면에서 사라짐. 실차 모드면 신호 수신을 멈추고 차량 서비스 연결을 정리한다. 데모 모드는 건드리지 않는다. */
    fun onBackground() {
        foreground = false
        speech.onBackground()
        if (_demo.value.mode == SignalMode.VEHICLE) engine.stop()
    }

    /**
     * `CAR_SPEED` 런타임 권한 요청의 결과. 실차 모드에서 화면에 보이는 중이면 소스를 다시 시작해 속도를 포함하거나(허용),
     * 거부 이유를 진단에 보이게 한다(거부). 소스는 시작할 때마다 권한을 새로 확인한다.
     * 데모 모드이거나 보이지 않는 중이면 아무 일도 하지 않는다(다음에 보일 때 새로 확인한다).
     */
    @Suppress("UNUSED_PARAMETER")
    fun onSpeedPermissionResult(granted: Boolean) {
        if (_demo.value.mode != SignalMode.VEHICLE || !foreground) return
        engine.stop()
        engine.start()
    }

    fun setDebugPanelVisible(visible: Boolean) {
        _demo.update { it.copy(debugPanelVisible = visible) }
    }

    /** 음성 켜기/끄기. 제한이 걸려 있으면 무시하고 false(FR-016). */
    fun setVoiceEnabled(on: Boolean): Boolean = speech.setVoiceEnabled(on)

    override fun onCleared() {
        speech.onBackground()
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
