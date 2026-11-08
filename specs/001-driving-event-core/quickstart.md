# Quickstart: 주행 이벤트 판정과 펫 감정 결정 코어 검증

이 기능이 끝났는지 확인하는 방법이다. 구현 코드는 `tasks.md` 와 구현 단계에서 쓴다.

## 준비

- 시스템에 JDK 가 없으므로 Android Studio 내장 JBR 을 쓴다.
  ```bash
  export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
  ```
- 차량이나 에뮬레이터는 필요 없다.

## 전체 테스트 실행

```bash
./gradlew :signal:testDebugUnitTest :engine:testDebugUnitTest --console=plain
```

기대 결과: `BUILD SUCCESSFUL`, 실패 0건, 전체 10초 안팎(SC-005). 이 모듈들은 빌드 캐시 때문에 두 번째 실행부터는 훨씬 빠르다.

## 검증 시나리오

| # | 확인할 것 | 관련 테스트 | 스펙 |
|-|-|-|-|
| 1 | 데모 출근 시나리오를 재생하면 이벤트가 `TRIP_START`, `SEATBELT_OK`, `HARD_CORNER`, `GOOD_LANE_CHANGE`, `LANE_DEPARTURE`, `HARD_BRAKE`, `TRIP_END` 순서로 나오고 점수가 94 | `DemoScenarioTest` | SC-001, US5 |
| 2 | 같은 시나리오를 100번 돌려도 이벤트, 감정 전환, 점수가 모두 같다 | `DemoScenarioTest` | SC-002 |
| 3 | 완만한 가속(0→50km/h, 8초)과 15km/h 미만의 방향 변화는 이벤트가 없다 | `EventDetectorTest` | SC-004, US1 |
| 4 | 제한 상태 `NONE`, `ACTIVE`, `UNKNOWN` 에서 같은 이벤트를 넣었을 때 등급이 `FULL`, `STATIC`, `STATIC` | `MotionGateTest`, `EngineCoreTest` | SC-003, US3 |
| 5 | 풀 모션 진행 중 제한이 걸리면 그 다음 출력부터 `STATIC` | `EngineCoreTest` | FR-017 |
| 6 | 깜짝 중 급제동은 버려지고, 어지러움 중 충돌 위험은 즉시 깜짝으로 전환 | `MoodMachineTest` | US2 |
| 7 | 깜빡이 없는 차선 변경은 감정이 안 바뀌고 −3점과 기록만 남는다 | `EngineCoreTest`, `ScoreKeeperTest` | US4 |
| 8 | 점수 가점 상한(+5)과 마지막에만 0~100 으로 자르기 | `ScoreKeeperTest` | FR-014 |
| 9 | 속도 정보가 없거나 간격이 2초를 넘는 샘플은 버려진다. 방위 359°→1° 는 +2° | `KinematicsTest` | Edge Cases |
| 10 | 주행 중에 앱이 켜지면 출발 이벤트 없이 주행이 시작되고, 주차 상태여도 속도가 0 이 아니면 도착이 아니다 | `TripTrackerTest` | FR-024, Edge Cases |
| 11 | 시나리오의 램프가 100ms 간격 신호로 풀린다 | `ScenarioExpandTest` | US5 |
| 12 | `MockSignalSource` 가 `IMMEDIATE` 와 `REAL_TIME`(가상 시간)에서 같은 신호 열을 낸다 | `MockSignalSourceTest` | FR-020 |
| 13 | `DrivingEngine` 이 `StateFlow` 로 출력을 내보내고 값이 바뀔 때만 갱신한다 | `DrivingEngineTest` | FR-018 |

## 수동 확인 (선택)

에뮬레이터 없이 로컬에서 시나리오를 눈으로 확인하려면, 테스트에서 `DemoScenarios.demoCommute` 를 `Pacing.IMMEDIATE` 로 재생하고 `DrivingEngine.events` 를 출력해 본다.
캐릭터 화면과 음성은 이 기능의 범위가 아니다(`PetView` 기능에서 확인한다).

## 완료 기준

- 위 1~13 이 자동 테스트로 존재하고 모두 통과한다.
- 테스트 없이 구현된 규칙이 없다(스펙의 FR 마다 대응하는 테스트가 있다).
- `docs/` 와 Pleos 문서 원문은 커밋하지 않았다.
