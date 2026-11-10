package com.example.dashpet.signal.car

import kotlinx.coroutines.flow.Flow

/** 차량 쪽에서 올라오는 원시 사건. Android 타입을 담지 않는다(`contracts/car-signal-source.md` 3절). */
sealed interface CarEvent {
    /** `PERF_VEHICLE_SPEED` 값(m/s). 변환 전 값이라 비정상 값이 있을 수 있다. */
    data class SpeedChanged(val mps: Float) : CarEvent

    /** `GEAR_SELECTION` 값(`VehicleGear`). */
    data class GearChanged(val gear: Int) : CarEvent

    /** `CarUxRestrictions` 의 두 값. */
    data class RestrictionChanged(val requiresDo: Boolean, val activeRestrictions: Int) : CarEvent

    /** 차량 서비스가 준비됨. 게이트웨이는 이어서 현재 값을 위 이벤트로 한 번씩 내보낸다. */
    data object Ready : CarEvent

    /** 차량 서비스가 끊김. */
    data object Lost : CarEvent

    /** 신호 하나를 쓸 수 있는지(권한, 지원 여부, 오류). */
    data class AvailabilityChanged(val signal: CarSignal, val availability: SignalAvailability) : CarEvent
}

enum class CarSignal { SPEED, GEAR, RESTRICTION }

/** 신호별로 값을 받을 수 있는지와 그 이유. */
enum class SignalAvailability {
    UNKNOWN,
    OK,
    NO_PERMISSION,
    UNSUPPORTED,
    ERROR,
}

/** 속도 구독 주기. 게이트웨이가 `CarPropertyManager.SENSOR_RATE_*` 로 옮긴다(`android.car` 를 이 파일에 들이지 않기 위해 enum 으로 둔다). */
enum class SpeedRate { ONCHANGE, NORMAL, UI, FAST }

/**
 * 실차 신호 소스 설정(한 곳).
 * [speedRate] 의 기본값 UI 는 `[확인 필요]`: 엔진의 가속 판단이 속도 간격에 민감한지 001 의 `EventConfig` 가정과 대조해야 한다.
 */
data class CarSignalConfig(
    /** 시간 진행 신호 간격(ms). 0 이하면 끈다. */
    val tickIntervalMs: Long = 250L,
    val speedRate: SpeedRate = SpeedRate.UI,
)

/**
 * 차량 서비스 접근 한 겹. [events] 는 수집을 시작하면 연결하고, 수집을 끝내면 모든 리스너 해제와 연결 해제를 하는 차가운 흐름이다.
 * 콜백은 어느 스레드에서 와도 되며, 순서와 시각은 소스가 책임진다.
 */
interface CarGateway {
    fun events(): Flow<CarEvent>
}
