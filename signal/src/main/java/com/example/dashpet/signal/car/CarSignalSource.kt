package com.example.dashpet.signal.car

import android.content.Context
import android.os.SystemClock
import com.example.dashpet.signal.ParkedState
import com.example.dashpet.signal.RawSignal
import com.example.dashpet.signal.RestrictionState
import com.example.dashpet.signal.Speed
import com.example.dashpet.signal.Tick
import com.example.dashpet.signal.VehicleSignalSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 실차 신호 소스. 차량 서비스([CarGateway])의 사건을 엔진 신호로 바꿔 내보낸다.
 *
 * 연결 수명은 [signals] 가 수집되는 동안에만 산다: `start()` 후 수집이 시작되면 게이트웨이 연결을 열고,
 * `stop()` 이나 수집 취소로 끝나면 게이트웨이가 리스너 해제와 연결 해제를 한다(`awaitClose`).
 * 다시 `start()` 하면 새 세션이다(게이트웨이를 새로 열고 같은 값도 한 번 더 낸다).
 *
 * 차량 콜백이 어느 스레드에서 와도 안전하도록 시각을 붙이고 내보내는 일을 한 락 안에서 한다.
 * 제한 값을 한 번도 받지 못한 동안은 신호를 내지 않는다. 엔진의 기본값이 `UNKNOWN`(정적)이기 때문이다.
 */
class CarSignalSource(
    private val gateway: CarGateway,
    private val clock: () -> Long,
    private val config: CarSignalConfig = CarSignalConfig(),
    private val log: (String) -> Unit = {},
) : VehicleSignalSource {
    private val running = MutableStateFlow(false)
    private val _diagnostics = MutableStateFlow(SignalDiagnostics.INITIAL)

    /**
     * 개발자용 진단 값. 수신을 멈추면 `link` 가 [CarLinkState.IDLE] 이 된다.
     * 차량은 같은 값을 자주 알린다(속도는 초당 여러 번). 엔진에 새 값이 가지 않는 반복에는 값이 바뀌지 않아야 한다.
     * 그렇지 않으면 정적 상태인데도 이 값을 보는 화면이 계속 다시 그려져 CPU 를 쓴다(T040 에서 에뮬레이터로 확인:
     * `lastUpdateMs` 를 매 이벤트마다 갱신했을 때 약 9fps, CPU 16%). `lastUpdateMs` 는 값이 실제로 바뀐 시각이다.
     */
    val diagnostics: StateFlow<SignalDiagnostics> = _diagnostics.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    override val signals: Flow<RawSignal> = running.transformLatest { on ->
        if (on) emitAll(session())
    }

    override fun start() {
        running.value = true
    }

    override fun stop() {
        running.value = false
    }

    private fun session(): Flow<RawSignal> = channelFlow {
        val mapper = CarSignalMapper(clock, startMs = clock())
        val lock = Any()
        // 시각을 붙이는 일과 보내는 일을 한 락 안에서 해서, 어느 스레드에서 오든 시각 순서가 곧 출력 순서가 된다.
        fun push(block: (CarSignalMapper) -> RawSignal?) = synchronized(lock) {
            block(mapper)?.let { trySend(it) }
        }

        setDiagnostics { SignalDiagnostics.INITIAL.copy(link = CarLinkState.CONNECTING) }

        if (config.tickIntervalMs > 0) {
            launch {
                while (isActive) {
                    delay(config.tickIntervalMs)
                    push { Tick(it.now()) }
                }
            }
        }
        launch {
            try {
                gateway.events().collect { event -> handle(event, ::push) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 차량 서비스 접근이 실패하면 제한을 알 수 없는 것으로 되돌린다. 엔진이 이전 값(예: 제한 없음)에
                // 머물면 풀 모션이 계속될 수 있어 안전하지 않다(헌장 I).
                log("gateway failed: ${e::class.simpleName}")
                var sent = false
                push { m -> m.lost()?.also { sent = true } }
                setDiagnostics {
                    it.copy(
                        link = CarLinkState.UNAVAILABLE,
                        restriction = if (sent) RestrictionState.UNKNOWN else it.restriction,
                    )
                }
            }
        }
        awaitClose { setDiagnostics { it.copy(link = CarLinkState.IDLE) } }
    }.buffer(Channel.UNLIMITED)

    private fun handle(event: CarEvent, push: ((CarSignalMapper) -> RawSignal?) -> Unit) {
        /** 매퍼로 신호를 만들어 보내고, 그 결과와 시각(시계를 다시 읽지 않음)으로 진단을 갱신한다. */
        fun <T : RawSignal> produce(
            make: (CarSignalMapper) -> T?,
            update: (SignalDiagnostics, T?, Long) -> SignalDiagnostics,
        ): T? {
            var out: T? = null
            var now = 0L
            push { m -> make(m).also { out = it; now = m.lastMs } }
            setDiagnostics { update(it, out, now) }
            return out
        }

        when (event) {
            CarEvent.Ready -> setDiagnostics { it.copy(link = CarLinkState.CONNECTED) }
            CarEvent.Lost -> produce({ it.lost() }) { d, sent, _ ->
                d.copy(link = CarLinkState.LOST, restriction = if (sent != null) RestrictionState.UNKNOWN else d.restriction)
            }
            is CarEvent.RestrictionChanged -> {
                val sent = produce({ it.restriction(event.requiresDo, event.activeRestrictions) }) { d, out, now ->
                    d.copy(
                        restriction = out?.state ?: d.restriction,
                        activeRestrictions = event.activeRestrictions,
                        restrictionAvailability = SignalAvailability.OK,
                        lastUpdateMs = if (out != null) now else d.lastUpdateMs,
                    )
                }
                // 엔진에 새 값을 보낸 때만 한 줄 남긴다(자주 오는 값은 남기지 않는다).
                sent?.let { log("restriction=${it.state} bits=0x${event.activeRestrictions.toString(16)}") }
            }
            is CarEvent.SpeedChanged -> {
                val wasMoving = (_diagnostics.value.speedMps ?: 0f) > 0f
                val sent = produce<Speed>({ it.speed(event.mps) }) { d, out, now ->
                    d.copy(
                        speedMps = out?.speedMps ?: d.speedMps,
                        speedAvailability = SignalAvailability.OK,
                        lastUpdateMs = if (out != null) now else d.lastUpdateMs,
                    )
                }
                // 속도는 자주 오므로 정지와 주행이 바뀔 때만 한 줄 남긴다.
                sent?.let { if ((it.speedMps > 0f) != wasMoving) log("moving=${it.speedMps > 0f} speedMps=${it.speedMps}") }
            }
            is CarEvent.GearChanged -> {
                val sent = produce<ParkedState>({ it.gear(event.gear) }) { d, out, now ->
                    d.copy(
                        parked = out?.parked ?: d.parked,
                        gearAvailability = SignalAvailability.OK,
                        lastUpdateMs = if (out != null) now else d.lastUpdateMs,
                    )
                }
                sent?.let { log("parked=${it.parked} gear=${event.gear}") }
            }
            is CarEvent.BrakePedalChanged -> {
                produce({ it.brake(event.pressed) }) { d, out, _ -> d.copy(brakePressed = out?.pressed ?: d.brakePressed) }
                    ?.let { log("brake pressed=${it.pressed}") } // 바뀔 때만 한 줄
            }
            is CarEvent.TurnSignalChanged -> {
                produce({ it.turnSignal(event.state) }) { d, out, _ -> d.copy(turnState = out?.state ?: d.turnState) }
                    ?.let { log("turnSignal=${it.state}") }
            }
            is CarEvent.SeatbeltWarningChanged -> {
                produce({ it.seatbelt(event.active) }) { d, out, _ -> d.copy(seatbeltWarning = out?.active ?: d.seatbeltWarning) }
                    ?.let { log("seatbeltWarning=${it.active}") }
            }
            is CarEvent.HeadingChanged -> {
                // 방위각은 자주 오므로 진단에 시각을 갱신하지 않는다(화면이 계속 다시 그려지지 않게, 위 속도와 같은 이유).
                // 값은 세션 동안 엔진이 쓸 수 있는 것만 보내고 로그에는 남기지 않는다.
                push { m -> m.heading(event.degrees) }
            }
            is CarEvent.AvailabilityChanged -> {
                setDiagnostics {
                    when (event.signal) {
                        CarSignal.SPEED -> it.copy(speedAvailability = event.availability)
                        CarSignal.GEAR -> it.copy(gearAvailability = event.availability)
                        CarSignal.RESTRICTION -> it.copy(restrictionAvailability = event.availability)
                        CarSignal.BRAKE -> it.copy(brakeAvailability = event.availability)
                        CarSignal.TURN_SIGNAL -> it.copy(turnSignalAvailability = event.availability)
                        CarSignal.SEATBELT -> it.copy(seatbeltAvailability = event.availability)
                        CarSignal.HEADING -> it.copy(headingAvailability = event.availability)
                    }
                }
                log("availability ${event.signal}=${event.availability}")
            }
        }
    }

    private fun setDiagnostics(change: (SignalDiagnostics) -> SignalDiagnostics) {
        var before: CarLinkState? = null
        var after: CarLinkState? = null
        _diagnostics.update {
            before = it.link
            change(it).also { next -> after = next.link }
        }
        if (before != after) log("link=$after")
    }

    companion object {
        /** 방위각(급커브)을 읽는 데 필요한 런타임 권한. 위치 권한 그룹이라 동의 뒤에만 요청한다. */
        const val HEADING_PERMISSION = FusedHeadingGateway.PERMISSION

        /** 속도(`PERF_VEHICLE_SPEED`)를 읽는 데 필요한 런타임 권한. `Car.PERMISSION_SPEED` 와 같은 값이다(`:app` 이 `android.car` 를 보지 않도록 문자열로 둔다). */
        const val SPEED_PERMISSION = "android.car.permission.CAR_SPEED"

        /**
         * 실제 차량에 연결하는 소스를 만든다. `:app` 이 `android.car` 를 직접 보지 않도록 여기서 게이트웨이와 시계를 고른다.
         * 시계는 단조 증가하는 `elapsedRealtime` 이다(벽시계 미사용).
         */
        fun create(
            context: Context,
            log: (String) -> Unit = {},
            config: CarSignalConfig = CarSignalConfig(),
        ): CarSignalSource =
            CarSignalSource(
                CompositeCarGateway(
                    primary = AndroidCarGateway(context.applicationContext, config.speedRate),
                    extras = listOf(
                        PleosVehicleGateway(context.applicationContext),
                        FusedHeadingGateway(context.applicationContext),
                    ),
                ),
                SystemClock::elapsedRealtime,
                config,
                log,
            )
    }
}
