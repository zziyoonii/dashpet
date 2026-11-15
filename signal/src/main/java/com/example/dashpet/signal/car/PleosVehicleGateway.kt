package com.example.dashpet.signal.car

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.SeatBeltNotBuckledWarningListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import android.annotation.SuppressLint
import android.content.Context
import com.example.dashpet.signal.TurnState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Pleos Vehicle SDK 로 브레이크 페달, 방향지시등, 안전벨트 미착용 경고를 읽는다(specs/005). 이 파일이 Vehicle SDK 를 만지는 유일한 곳이다.
 * JVM 에서는 검증할 수 없어 에뮬레이터 확인으로 대신한다.
 *
 * 수명: [events] 를 수집하는 동안에만 `Vehicle` 을 만든다. 순서는 생성 -> `initialize()` -> 지원 확인 -> 리스너 등록 -> 현재 값 읽기이고,
 * 수집이 끝나면 등록한 리스너를 같은 객체로 모두 해제한 뒤 `release()` 한다(해제한 인스턴스는 다시 쓰지 않는다).
 * 신호마다 지원 확인(`checkXxxCapabilities`)을 먼저 하고 읽기 권한이 없는 신호는 [CarSignal] 별 가용성으로 알린다. 하나가 실패해도 나머지는 계속한다.
 * 콜백 스레드는 문서에 명시가 없다 `[확인 필요]`. 값은 `trySend` 로만 넘긴다.
 */
@SuppressLint("MissingPermission") // 세 권한(CAR_DRIVE, CAR_LIGHTS, CAR_SAFETY)은 normal 이고 매니페스트에 선언한다.
internal class PleosVehicleGateway(private val context: Context) : CarGateway {

    override fun events(): Flow<CarEvent> = callbackFlow {
        val closed = AtomicBoolean(false)
        var vehicle: Vehicle? = null
        val cleanups = mutableListOf<() -> Unit>() // 등록한 리스너를 해제하는 일. 수집이 끝날 때 거꾸로 실행한다.
        val lock = Any()

        fun addCleanup(block: () -> Unit) = synchronized(lock) { cleanups += block }

        try {
            val v = Vehicle(context.applicationContext)
            vehicle = v
            v.initialize()
            startBrake(v, closed, ::addCleanup)
            startTurnSignal(v, closed, ::addCleanup)
            startSeatbelt(v, closed, ::addCleanup)
        } catch (e: Exception) {
            for (signal in listOf(CarSignal.BRAKE, CarSignal.TURN_SIGNAL, CarSignal.SEATBELT)) report(signal, SignalAvailability.ERROR)
        }

        awaitClose {
            closed.set(true)
            val toRun = synchronized(lock) { cleanups.reversed().also { cleanups.clear() } }
            toRun.forEach { runCatching(it) }
            runCatching { vehicle?.release() }
        }
    }.buffer(Channel.UNLIMITED)

    // --- 브레이크 페달 ---

    private fun ProducerScope<CarEvent>.startBrake(v: Vehicle, closed: AtomicBoolean, addCleanup: (() -> Unit) -> Unit) {
        val brake = v.getBrake()
        val listener = object : BrakePedalStatusListener {
            override fun onBrakePedalStatusUpdated(status: Boolean?) {
                if (status != null) trySend(CarEvent.BrakePedalChanged(status))
            }

            override fun onFailed(e: Exception) = report(CarSignal.BRAKE, e.toAvailability())
        }
        brake.checkBrakePedalStatusCapabilities(
            onComplete = { config ->
                if (closed.get()) return@checkBrakePedalStatusCapabilities
                if (config.accessType == AccessType.NONE) return@checkBrakePedalStatusCapabilities report(CarSignal.BRAKE, SignalAvailability.UNSUPPORTED)
                try {
                    // 리스너를 먼저 등록한 뒤 현재 값을 읽어 그 사이의 변화를 놓치지 않는다(같은 값은 소스가 걸러낸다).
                    brake.registerBrakePedalStatus(listener)
                    addCleanup { brake.unregisterBrakePedalStatus(listener) }
                    brake.getCurrentBrakePedalStatus(
                        onComplete = { if (it != null) trySend(CarEvent.BrakePedalChanged(it)) },
                        onFailed = { report(CarSignal.BRAKE, it.toAvailability()) },
                    )
                    report(CarSignal.BRAKE, SignalAvailability.OK)
                } catch (e: Exception) {
                    report(CarSignal.BRAKE, e.toAvailability())
                }
            },
            onFailed = { report(CarSignal.BRAKE, it.toAvailability()) },
        )
    }

    // --- 방향지시등 ---

    private fun ProducerScope<CarEvent>.startTurnSignal(v: Vehicle, closed: AtomicBoolean, addCleanup: (() -> Unit) -> Unit) {
        val turn = v.getTurnSignal()
        val listener = object : TurnSignalStateListener {
            override fun onTurnSignalStateUpdated(state: TurnSignalValue?) {
                if (state != null) trySend(CarEvent.TurnSignalChanged(state.toTurnState()))
            }

            override fun onFailed(e: Exception) = report(CarSignal.TURN_SIGNAL, e.toAvailability())
        }
        turn.checkTurnSignalStateCapabilities(
            onComplete = { config ->
                if (closed.get()) return@checkTurnSignalStateCapabilities
                if (config.accessType == AccessType.NONE) return@checkTurnSignalStateCapabilities report(CarSignal.TURN_SIGNAL, SignalAvailability.UNSUPPORTED)
                try {
                    turn.registerTurnSignalState(listener)
                    addCleanup { turn.unregisterTurnSignalState(listener) }
                    turn.getCurrentTurnSignalState(
                        onComplete = { if (it != null) trySend(CarEvent.TurnSignalChanged(it.toTurnState())) },
                        onFailed = { report(CarSignal.TURN_SIGNAL, it.toAvailability()) },
                    )
                    report(CarSignal.TURN_SIGNAL, SignalAvailability.OK)
                } catch (e: Exception) {
                    report(CarSignal.TURN_SIGNAL, e.toAvailability())
                }
            },
            onFailed = { report(CarSignal.TURN_SIGNAL, it.toAvailability()) },
        )
    }

    // --- 안전벨트 미착용 경고 ---

    private fun ProducerScope<CarEvent>.startSeatbelt(v: Vehicle, closed: AtomicBoolean, addCleanup: (() -> Unit) -> Unit) {
        val safety = v.getSafety()
        safety.checkSeatNotBuckledWarningCapabilities(
            onComplete = { config, areas ->
                if (closed.get()) return@checkSeatNotBuckledWarningCapabilities
                // 운전석 먼저. 차종별로 지원 좌석이 다르므로 목록에서 고른다(우핸들 차량은 [확인 필요]).
                val area = areas.firstOrNull { it == DRIVER_SEAT } ?: areas.firstOrNull()
                if (config.accessType == AccessType.NONE || area == null) {
                    return@checkSeatNotBuckledWarningCapabilities report(CarSignal.SEATBELT, SignalAvailability.UNSUPPORTED)
                }
                val listener = object : SeatBeltNotBuckledWarningListener {
                    override fun onSeatBeltNotBuckledWarningUpdated(state: Boolean?) {
                        if (state != null) trySend(CarEvent.SeatbeltWarningChanged(state))
                    }

                    override fun onFailed(e: Exception) = report(CarSignal.SEATBELT, e.toAvailability())
                }
                try {
                    safety.registerSeatBeltNotBuckledWarning(area, listener)
                    addCleanup { safety.unregisterSeatBeltNotBuckledWarning(area, listener) }
                    safety.hasSeatBeltNotBuckledWarning(
                        area,
                        onComplete = { if (it != null) trySend(CarEvent.SeatbeltWarningChanged(it)) },
                        onFailed = { report(CarSignal.SEATBELT, it.toAvailability()) },
                    )
                    report(CarSignal.SEATBELT, SignalAvailability.OK)
                } catch (e: Exception) {
                    report(CarSignal.SEATBELT, e.toAvailability())
                }
            },
            onFailed = { report(CarSignal.SEATBELT, it.toAvailability()) },
        )
    }

    // --- 공통 ---

    private fun ProducerScope<CarEvent>.report(signal: CarSignal, availability: SignalAvailability) {
        trySend(CarEvent.AvailabilityChanged(signal, availability))
    }

    private fun TurnSignalValue.toTurnState(): TurnState = when (this) {
        TurnSignalValue.OFF -> TurnState.OFF
        TurnSignalValue.LEFT -> TurnState.LEFT
        TurnSignalValue.RIGHT -> TurnState.RIGHT
    }

    /**
     * 오류 메시지로 가용성을 나눈다. SDK 의 `VehicleException.message` 는 오류 코드 이름(예: ERROR_NOT_SUPPORTED_PROPERTY)이다.
     * 배포된 AAR 에서는 이 예외 클래스를 직접 참조할 수 없어(난독화) 메시지를 쓴다. 권한 오류와 미지원 오류가 아니면 일반 오류로 본다.
     */
    private fun Exception.toAvailability(): SignalAvailability {
        if (this is SecurityException) return SignalAvailability.NO_PERMISSION
        val name = message.orEmpty().uppercase()
        return when {
            name.contains("PERMISSION") -> SignalAvailability.NO_PERMISSION
            name.contains("NOT_SUPPORTED") -> SignalAvailability.UNSUPPORTED
            else -> SignalAvailability.ERROR
        }
    }

    private companion object {
        val DRIVER_SEAT = SeatArea(SeatValue.ROW_1_LEFT)
    }
}
