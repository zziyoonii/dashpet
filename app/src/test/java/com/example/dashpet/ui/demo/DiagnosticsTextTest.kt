package com.example.dashpet.ui.demo

import com.example.dashpet.signal.RestrictionState
import com.example.dashpet.signal.car.CarLinkState
import com.example.dashpet.signal.car.SignalAvailability
import com.example.dashpet.signal.car.SignalDiagnostics
import org.junit.Assert.assertEquals
import org.junit.Test

class DiagnosticsTextTest {
    @Test fun connectedWithAllValues() {
        val d = SignalDiagnostics.INITIAL.copy(
            link = CarLinkState.CONNECTED,
            restriction = RestrictionState.ACTIVE,
            activeRestrictions = 0x50,
            speedMps = 11.111112f,
            parked = false,
            speedAvailability = SignalAvailability.OK,
            gearAvailability = SignalAvailability.OK,
            restrictionAvailability = SignalAvailability.OK,
            lastUpdateMs = 12_300,
        )
        assertEquals(
            listOf("연결: 연결됨", "제한: 있음 (0x50)", "속도: 40.0 km/h (11.1 m/s)", "기어: 주차 아님", "마지막 갱신: 12.3초"),
            signalDiagnosticLines(d),
        )
    }

    @Test fun parkedAndNoRestriction() {
        val d = SignalDiagnostics.INITIAL.copy(
            link = CarLinkState.CONNECTED,
            restriction = RestrictionState.NONE,
            activeRestrictions = 0,
            speedMps = 0f,
            parked = true,
            speedAvailability = SignalAvailability.OK,
            gearAvailability = SignalAvailability.OK,
            restrictionAvailability = SignalAvailability.OK,
            lastUpdateMs = 0,
        )
        assertEquals(
            listOf("연결: 연결됨", "제한: 없음 (0x0)", "속도: 0.0 km/h (0.0 m/s)", "기어: 주차", "마지막 갱신: 0.0초"),
            signalDiagnosticLines(d),
        )
    }

    @Test fun initialShowsWaitingAndChecking() {
        assertEquals(
            listOf("연결: 대기", "제한: 알 수 없음 · 확인 중", "속도: - · 확인 중", "기어: - · 확인 중", "마지막 갱신: -"),
            signalDiagnosticLines(SignalDiagnostics.INITIAL),
        )
    }

    @Test fun reasonsAreShownPerSignal() {
        val d = SignalDiagnostics.INITIAL.copy(
            link = CarLinkState.CONNECTED,
            speedAvailability = SignalAvailability.NO_PERMISSION,
            gearAvailability = SignalAvailability.UNSUPPORTED,
            restrictionAvailability = SignalAvailability.ERROR,
        )
        assertEquals(
            listOf("연결: 연결됨", "제한: 알 수 없음 · 오류", "속도: - · 권한 거부", "기어: - · 미지원", "마지막 갱신: -"),
            signalDiagnosticLines(d),
        )
    }

    @Test fun linkNames() {
        fun link(l: CarLinkState) = signalDiagnosticLines(SignalDiagnostics.INITIAL.copy(link = l)).first()
        assertEquals("연결: 연결 중", link(CarLinkState.CONNECTING))
        assertEquals("연결: 끊김", link(CarLinkState.LOST))
        assertEquals("연결: 사용 불가", link(CarLinkState.UNAVAILABLE))
    }
}
