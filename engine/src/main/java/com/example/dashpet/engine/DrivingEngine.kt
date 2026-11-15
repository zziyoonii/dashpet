package com.example.dashpet.engine

import com.example.dashpet.signal.RawSignal
import com.example.dashpet.signal.Restriction
import com.example.dashpet.signal.RestrictionState
import com.example.dashpet.signal.VehicleSignalSource
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * [EngineCore] 를 코루틴으로 감싼다. 모든 코어 호출은 한 코루틴에서 순서대로 하고, 호출한 [scope] 의
 * 디스패처에서 동작한다. 메인 스레드 전환은 구독하는 쪽의 책임이다.
 * `start()` 는 새 세션이다: 엔진 상태(주행 기록 포함)를 새로 만들고 소스를 시작한다.
 * [injectRestriction] 의 값은 소스 신호와 같은 코루틴에서 순서대로 처리된다.
 */
class DrivingEngine(
    private val source: VehicleSignalSource,
    private val scope: CoroutineScope,
    private val config: EngineConfig = EngineConfig(),
) {
    private var core = EngineCore(config)
    private var job: Job? = null

    /** 수동 제한 상태 주입 대기열. 어느 스레드에서 넣어도 안전하고, 처리는 수집 코루틴 한 곳에서만 한다. */
    private val injected = Channel<RestrictionState>(Channel.UNLIMITED)

    private val _output = MutableStateFlow(PetOutput.INITIAL)
    private val _events = MutableSharedFlow<DrivingEvent>(extraBufferCapacity = EVENT_BUFFER)

    /** 최신 출력. 구독하면 마지막 값을 즉시 받고, 시각을 뺀 내용이 바뀔 때만 갱신된다. */
    val output: StateFlow<PetOutput> = _output.asStateFlow()

    private val _completedTrip = MutableStateFlow<Trip?>(null)

    /** 방금 끝난 주행(도착 리포트용). `TRIP_END` 가 확정될 때 갱신되고, 새 세션(`start()`)에서 비워진다. 위치 값은 담지 않는다. */
    val completedTrip: StateFlow<Trip?> = _completedTrip.asStateFlow()

    /** 확정된 이벤트 스트림. 구독 전의 이벤트는 받지 않는다. */
    val events: SharedFlow<DrivingEvent> = _events.asSharedFlow()

    fun start() {
        if (job?.isActive == true) return
        core = EngineCore(config)
        _output.value = PetOutput.INITIAL
        _completedTrip.value = null
        // 수집을 먼저 시작한 뒤 소스를 켜서 첫 신호를 놓치지 않는다.
        job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            // 소스 신호와 주입 신호를 한 수집 코루틴에서만 코어에 넣는다(순서 보장).
            var lastMs = 0L // 마지막으로 처리한 신호의 시각. 처리한 신호가 없으면 0
            merge(
                source.signals.map { Input.FromSource(it) },
                injected.receiveAsFlow().map { Input.Injected(it) },
            ).collect { input ->
                // 주입 신호의 시각은 수집 코루틴에서 정한다(= 앞서 처리한 신호의 시각).
                val signal: RawSignal = when (input) {
                    is Input.FromSource -> input.signal
                    is Input.Injected -> Restriction(lastMs, input.state)
                }
                val step = core.process(signal)
                lastMs = maxOf(lastMs, signal.timestampMs)
                if (!step.output.sameStateAs(_output.value)) _output.value = step.output
                if (step.events.any { it.type == EventType.TRIP_END }) _completedTrip.value = core.completedTrips().lastOrNull()
                step.events.forEach { _events.emit(it) }
            }
        }
        source.start()
    }

    /**
     * 수동으로 주행 제한 상태를 바꾼다(데모, 확인용). 엔진이 마지막으로 처리한 신호의 시각으로 `Restriction`
     * 신호를 만들어 소스 신호와 같은 경로에서 처리한다(처리한 신호가 없으면 시각 0). `start()` 전에 호출하면
     * `start()` 뒤에 처리하고, `stop()` 하면 대기 중인 값은 버린다. 다음 시나리오 `Restriction` 신호가 올 때까지 유지된다.
     * 어느 스레드에서 호출해도 안전하다.
     */
    fun injectRestriction(state: RestrictionState) {
        injected.trySend(state)
    }

    fun stop() {
        source.stop()
        job?.cancel()
        job = null
        while (injected.tryReceive().isSuccess) Unit // 대기 중인 주입은 버린다(새 세션)
    }

    private sealed interface Input {
        class FromSource(val signal: RawSignal) : Input
        class Injected(val state: RestrictionState) : Input
    }

    private companion object {
        const val EVENT_BUFFER = 64
    }
}
