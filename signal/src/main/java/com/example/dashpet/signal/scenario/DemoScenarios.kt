package com.example.dashpet.signal.scenario

import com.example.dashpet.signal.RestrictionState
import com.example.dashpet.signal.Side
import com.example.dashpet.signal.TurnState
import com.example.dashpet.signal.WarningType

object DemoScenarios {
    /**
     * 데모 출근 시나리오(52초, 개발 명세서 9장). 기대 결과: 이벤트 TRIP_START, SEATBELT_OK, HARD_CORNER,
     * GOOD_LANE_CHANGE, LANE_DEPARTURE, HARD_BRAKE, TRIP_END 7개와 점수 94.
     */
    val demoCommute: Scenario = scenario("demo-commute") {
        // 초기 상태: 이게 있어야 8초의 출발이 TRIP_START 가 된다.
        at(0) {
            parked(true)
            speed(0)
            heading(90)
            brakePedal(false)
            turnSignal(TurnState.OFF)
            restriction(RestrictionState.NONE)
        }
        at(8_000) {
            restriction(RestrictionState.ACTIVE) // 출발과 동시에 제한을 먼저 걸어 풀 모션이 새지 않게 한다
            parked(false) // 벨트 경고 켜짐보다 먼저
            warning(WarningType.SEATBELT, true)
        }
        at(10_000) { warning(WarningType.SEATBELT, false) }
        at(12_000) { speedRamp(0, 50, 8_000) } // 완만한 가속 (약 1.7 m/s²)
        at(22_000) { headingTurn(30, 1_000) } // 급커브 (우회전)
        at(30_000) { turnSignal(TurnState.RIGHT) }
        at(32_000) { laneChange(Side.RIGHT) }
        at(33_000) { turnSignal(TurnState.OFF) }
        at(38_000) { warning(WarningType.LANE_DEPARTURE, true) }
        at(40_000) { warning(WarningType.LANE_DEPARTURE, false) }
        at(46_000) {
            brakePedal(true)
            speedRamp(50, 0, 2_000) // 급제동
        }
        at(52_000) {
            parked(true)
            restriction(RestrictionState.NONE)
        }
    }
}
