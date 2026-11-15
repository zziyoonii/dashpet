package com.example.dashpet.signal.car

import com.example.dashpet.signal.BrakePedal
import com.example.dashpet.signal.Heading
import com.example.dashpet.signal.ParkedState
import com.example.dashpet.signal.Restriction
import com.example.dashpet.signal.RestrictionState
import com.example.dashpet.signal.Speed
import com.example.dashpet.signal.TurnSignal
import com.example.dashpet.signal.TurnState
import com.example.dashpet.signal.VehicleWarning
import com.example.dashpet.signal.WarningType
import kotlin.math.max

/**
 * 차량 원시 값을 엔진 신호로 바꾸는 규칙(순수, Android API 없음). 세션마다 새로 만든다.
 * 시각은 소스 시작을 0 으로 하는 단조 증가 값이다: `max(직전 시각, clock() - startMs)`(FR-008).
 * 어느 호출이든 시계를 한 번 읽으므로 중복 제거로 null 을 돌려줘도 시각 기준은 이어진다.
 * 스레드 안전하지 않다. 호출하는 쪽(CarSignalSource)이 한 락 안에서 쓴다.
 */
class CarSignalMapper(private val clock: () -> Long, private val startMs: Long) {
    private var lastTimeMs = 0L
    private var lastRestriction: RestrictionState? = null
    private var lastSpeedMps: Float? = null
    private var lastParked: Boolean? = null
    private var lastBrake: Boolean? = null
    private var lastTurn: TurnState? = null
    private var lastSeatbelt: Boolean? = null

    /** 가장 최근 호출에서 쓴 시각. 호출 직후 진단에 같은 시각을 쓰려고 시계를 다시 읽지 않게 한다. */
    val lastMs: Long get() = lastTimeMs

    /** 지금 시각(소스 시작 기준 ms). 직전 값보다 작아지지 않는다. */
    fun now(): Long {
        lastTimeMs = max(lastTimeMs, clock() - startMs)
        return lastTimeMs
    }

    /**
     * 제한 규칙: `requiresDo || active != 0` 이면 [RestrictionState.ACTIVE], 아니면 [RestrictionState.NONE].
     * 속도와 기어는 제한 판단에 쓰지 않는다(FR-004). 직전에 보낸 값과 같으면 null.
     */
    fun restriction(requiresDo: Boolean, active: Int): Restriction? {
        val t = now()
        val state = if (requiresDo || active != 0) RestrictionState.ACTIVE else RestrictionState.NONE
        if (state == lastRestriction) return null
        lastRestriction = state
        return Restriction(t, state)
    }

    /** 차량 서비스가 끊김: 제한을 알 수 없음으로. 이미 알 수 없음으로 보냈으면 null. */
    fun lost(): Restriction? {
        val t = now()
        // 끊겼다 돌아오면 같은 값도 다시 알려야 하므로 값 비교 기준을 비운다.
        lastBrake = null
        lastTurn = null
        lastSeatbelt = null
        if (lastRestriction == RestrictionState.UNKNOWN) return null
        lastRestriction = RestrictionState.UNKNOWN
        return Restriction(t, RestrictionState.UNKNOWN)
    }

    /**
     * 속도(m/s). 유한하고 0 이상이면 그대로, 0 근처의 미세한 음수(`-SPEED_EPSILON_MPS` 이상)는 0 으로 보정하고,
     * 그 밖의 음수, NaN, 무한대는 버린다(엔진에 들어가지 않는다). 직전에 보낸 값과 같으면 null.
     * 버려진 값은 중복 제거 기준에 영향을 주지 않는다.
     */
    fun speed(mps: Float): Speed? {
        val t = now()
        if (!mps.isFinite()) return null
        val value = when {
            mps > 0f -> mps
            mps >= -SPEED_EPSILON_MPS -> 0f // -0.0f 도 여기서 0f 로 정규화된다
            else -> return null
        }
        if (value == lastSpeedMps) return null
        lastSpeedMps = value
        return Speed(t, value)
    }

    /**
     * 기어 → 주차 여부. P 면 `parked=true`, 0(알 수 없음)이 아닌 그 밖의 값은 `parked=false`. 알 수 없으면 null(신호 없음).
     * 직전에 보낸 주차 여부와 같으면 null(D 에서 R 로 바뀌어도 둘 다 주차 아님이라 새 정보가 없다).
     */
    fun gear(gear: Int): ParkedState? {
        val t = now()
        if (gear == GEAR_UNKNOWN) return null
        val parked = gear == GEAR_PARK
        if (parked == lastParked) return null
        lastParked = parked
        return ParkedState(t, parked)
    }

    /** 브레이크 페달. 직전에 보낸 값과 같으면 null. */
    fun brake(pressed: Boolean): BrakePedal? {
        val t = now()
        if (pressed == lastBrake) return null
        lastBrake = pressed
        return BrakePedal(t, pressed)
    }

    /** 방향지시등. 직전에 보낸 값과 같으면 null. */
    fun turnSignal(state: TurnState): TurnSignal? {
        val t = now()
        if (state == lastTurn) return null
        lastTurn = state
        return TurnSignal(t, state)
    }

    /** 안전벨트 미착용 경고. 직전에 보낸 값과 같으면 null. */
    fun seatbelt(active: Boolean): VehicleWarning? {
        val t = now()
        if (active == lastSeatbelt) return null
        lastSeatbelt = active
        return VehicleWarning(t, WarningType.SEATBELT, active)
    }

    /**
     * 방위각(도). 0 이상 360 이하의 유한한 값만 받는다(360 은 0 으로 정규화). 모를 때 쓰는 표식(-1 등)과 범위 밖 값은 버린다.
     * 같은 값이 이어져도 보낸다: 엔진은 샘플 간격으로 변화율을 구하므로 시각이 진행되어야 한다.
     * `[확인 필요]` FusedLocation heading 의 단위와 기준 방향은 문서에 없다(도, 북 기준 시계 방향으로 가정).
     */
    fun heading(deg: Float): Heading? {
        val t = now()
        if (!deg.isFinite() || deg < 0f || deg > 360f) return null
        return Heading(t, deg)
    }

    companion object {
        /** 0 근처 미세한 음수를 0 으로 보정하는 한계(m/s). `[확인 필요]` 차량이 실제로 이런 값을 주는지는 에뮬레이터에서 보지 못했다. */
        const val SPEED_EPSILON_MPS = 1e-3f

        // android.car.VehicleGear 값(스텁에서 확인). 이 파일은 android.car 를 import 하지 않으므로 값으로 적는다.
        private const val GEAR_UNKNOWN = 0
        private const val GEAR_PARK = 4
    }
}
