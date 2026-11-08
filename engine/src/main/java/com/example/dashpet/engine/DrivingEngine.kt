package com.example.dashpet.engine

import com.example.dashpet.signal.VehicleSignalSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * [EngineCore] 를 코루틴으로 감싼다. 모든 코어 호출은 한 코루틴에서 순서대로 하고, 호출한 [scope] 의
 * 디스패처에서 동작한다. 메인 스레드 전환은 구독하는 쪽의 책임이다.
 * `start()` 는 새 세션이다: 엔진 상태(주행 기록 포함)를 새로 만들고 소스를 시작한다.
 */
class DrivingEngine(
    private val source: VehicleSignalSource,
    private val scope: CoroutineScope,
    private val config: EngineConfig = EngineConfig(),
) {
    private var core = EngineCore(config)
    private var job: Job? = null

    private val _output = MutableStateFlow(PetOutput.INITIAL)
    private val _events = MutableSharedFlow<DrivingEvent>(extraBufferCapacity = EVENT_BUFFER)

    /** 최신 출력. 구독하면 마지막 값을 즉시 받고, 시각을 뺀 내용이 바뀔 때만 갱신된다. */
    val output: StateFlow<PetOutput> = _output.asStateFlow()

    /** 확정된 이벤트 스트림. 구독 전의 이벤트는 받지 않는다. */
    val events: SharedFlow<DrivingEvent> = _events.asSharedFlow()

    fun start() {
        if (job?.isActive == true) return
        core = EngineCore(config)
        _output.value = PetOutput.INITIAL
        // 수집을 먼저 시작한 뒤 소스를 켜서 첫 신호를 놓치지 않는다.
        job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            source.signals.collect { signal ->
                val step = core.process(signal)
                if (!step.output.sameStateAs(_output.value)) _output.value = step.output
                step.events.forEach { _events.emit(it) }
            }
        }
        source.start()
    }

    fun stop() {
        source.stop()
        job?.cancel()
        job = null
    }

    private companion object {
        const val EVENT_BUFFER = 64
    }
}
