package com.example.dashpet.ui.demo

import com.example.dashpet.signal.RestrictionState
import com.example.dashpet.signal.car.CarLinkState
import com.example.dashpet.signal.car.SignalAvailability
import com.example.dashpet.signal.car.SignalDiagnostics
import java.util.Locale

/**
 * 실차 신호 진단을 화면에 보일 줄로 바꾼다(`contracts/car-signal-source.md` 6절). Android API 를 쓰지 않는다.
 * 값을 받지 못한 신호는 "-" 와 이유(권한 거부, 미지원, 오류, 확인 중)를 함께 보인다.
 */
fun signalDiagnosticLines(d: SignalDiagnostics): List<String> {
    fun reason(a: SignalAvailability): String = when (a) {
        SignalAvailability.OK -> ""
        SignalAvailability.UNKNOWN -> " · 확인 중"
        SignalAvailability.NO_PERMISSION -> " · 권한 거부"
        SignalAvailability.UNSUPPORTED -> " · 미지원"
        SignalAvailability.ERROR -> " · 오류"
    }

    val link = when (d.link) {
        CarLinkState.IDLE -> "대기"
        CarLinkState.CONNECTING -> "연결 중"
        CarLinkState.CONNECTED -> "연결됨"
        CarLinkState.UNAVAILABLE -> "사용 불가"
        CarLinkState.LOST -> "끊김"
    }
    val restriction = when (d.restriction) {
        RestrictionState.ACTIVE -> "있음"
        RestrictionState.NONE -> "없음"
        RestrictionState.UNKNOWN -> "알 수 없음"
    }
    val bits = d.activeRestrictions?.let { " (0x${it.toString(16)})" } ?: ""
    val speed = d.speedMps?.let {
        String.format(Locale.ROOT, "%.1f km/h (%.1f m/s)", it * 3.6f, it)
    } ?: "-"
    val gear = when (d.parked) {
        true -> "주차"
        false -> "주차 아님"
        null -> "-"
    }
    val updated = d.lastUpdateMs?.let { String.format(Locale.ROOT, "%.1f초", it / 1000.0) } ?: "-"

    return listOf(
        "연결: $link",
        "제한: $restriction$bits${reason(d.restrictionAvailability)}",
        "속도: $speed${reason(d.speedAvailability)}",
        "기어: $gear${reason(d.gearAvailability)}",
        "마지막 갱신: $updated",
    )
}
