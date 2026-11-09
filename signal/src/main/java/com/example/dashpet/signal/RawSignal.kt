package com.example.dashpet.signal

/** 차량에서 들어오는 시각이 붙은 입력 하나. 같은 스트림 안에서 timestampMs 는 감소하지 않는다. */
sealed interface RawSignal {
    val timestampMs: Long
}

enum class TurnState { OFF, LEFT, RIGHT }

enum class WarningType { SEATBELT, LANE_DEPARTURE, COLLISION, PROXIMITY }

enum class Side { LEFT, RIGHT }

/** 주행 제한 상태. UNKNOWN 은 제한이 있는 것으로 취급한다. */
enum class RestrictionState { NONE, ACTIVE, UNKNOWN }

/** 속도(m/s). 0 이상이며 음수, NaN, 무한대는 거부한다. */
data class Speed(override val timestampMs: Long, val speedMps: Float) : RawSignal {
    init {
        require(speedMps >= 0f && speedMps.isFinite()) { "speedMps must be finite and >= 0: $speedMps" }
    }
}

/** 방위각(도). 0 이상 360 미만, 시계 방향이 증가. 범위 밖 값은 360 으로 나눈 나머지로 정규화한다. */
class Heading(override val timestampMs: Long, headingDeg: Float) : RawSignal {
    val headingDeg: Float

    init {
        require(headingDeg.isFinite()) { "headingDeg must be finite: $headingDeg" }
        this.headingDeg = ((headingDeg % 360f) + 360f) % 360f
    }

    override fun equals(other: Any?): Boolean =
        other is Heading && other.timestampMs == timestampMs && other.headingDeg == headingDeg

    override fun hashCode(): Int = 31 * timestampMs.hashCode() + headingDeg.hashCode()

    override fun toString(): String = "Heading(timestampMs=$timestampMs, headingDeg=$headingDeg)"
}

data class ParkedState(override val timestampMs: Long, val parked: Boolean) : RawSignal

data class BrakePedal(override val timestampMs: Long, val pressed: Boolean) : RawSignal

data class TurnSignal(override val timestampMs: Long, val state: TurnState) : RawSignal

data class VehicleWarning(
    override val timestampMs: Long,
    val type: WarningType,
    val active: Boolean,
) : RawSignal

data class LaneChange(override val timestampMs: Long, val direction: Side) : RawSignal

data class Restriction(override val timestampMs: Long, val state: RestrictionState) : RawSignal

/**
 * 값 없이 "시간이 여기까지 왔다"만 알리는 신호. 신호가 없는 구간에서도 엔진이 시간을 진행해
 * 지속 시간이 있는 감정을 만료시킬 수 있게 한다. 소스는 자기 시간 기준으로 주기적으로 방출한다.
 */
data class Tick(override val timestampMs: Long) : RawSignal
