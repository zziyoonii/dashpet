# Data Model: 주행 이벤트 판정과 펫 감정 결정 코어

필드와 규칙만 적는다. 구현 코드는 `tasks.md` 와 구현 단계에서 쓴다. `[S]` 는 `:signal` 모듈, `[E]` 는 `:engine` 모듈에 둔다는 뜻이다.

## 입력 [S]

### RawSignal

모든 신호는 `timestampMs`(Long, 같은 스트림 안에서 감소하지 않음)를 가진다. 같은 시각의 신호는 입력 순서를 유지한다.

| 신호 | 값 | 검증 |
|-|-|-|
| `Speed` | `speedMps: Float` | 0 이상. 음수는 거부 |
| `Heading` | `headingDeg: Float` | 0 이상 360 미만(나침반, 시계 방향이 증가). 범위 밖은 360 으로 나눈 나머지로 정규화 |
| `ParkedState` | `parked: Boolean` | - |
| `BrakePedal` | `pressed: Boolean` | - |
| `TurnSignal` | `state: TurnState` | - |
| `VehicleWarning` | `type: WarningType`, `active: Boolean` | - |
| `LaneChange` | `direction: Side` | - |
| `Restriction` | `state: RestrictionState` | - |

열거형:
- `TurnState`: `OFF`, `LEFT`, `RIGHT` (조사 결과 공개 API 가 세 값뿐이라 `HAZARD` 는 두지 않는다)
- `WarningType`: `SEATBELT`, `LANE_DEPARTURE`, `COLLISION`, `PROXIMITY`
- `Side`: `LEFT`, `RIGHT`
- `RestrictionState`: `NONE`(제한 없음), `ACTIVE`(제한 있음), `UNKNOWN`

### Scenario

- `name: String`
- `steps`: 시간순 단계 목록. 각 단계는 시각(ms)과 다음 중 하나다: 단일 신호, `speedRamp(from, to, durationMs)`, `headingTurn(deltaDeg, durationMs)`.
- `expand(): List<RawSignal>`: 램프와 회전을 10Hz(100ms 간격) 신호로 풀어서 시간순으로 돌려준다. 같은 시각이면 단계 입력 순서를 유지한다. 부수 효과 없는 순수 함수다.
- 검증: 단계 시각은 감소하지 않음. `durationMs` 는 양수.

## 판정 결과 [E]

### EventType (FR-001)

`TRIP_START`, `SEATBELT_OK`, `HARD_ACCEL`, `HARD_BRAKE`, `HARD_CORNER`, `LANE_DEPARTURE`, `COLLISION_RISK`, `GOOD_LANE_CHANGE`, `NO_SIGNAL_LANE_CHANGE`, `TRIP_END`

### Direction4

몸이 쏠리는 쪽: `FRONT`, `BACK`, `LEFT`, `RIGHT`.

### DrivingEvent (FR-006, FR-013)

| 필드 | 설명 |
|-|-|
| `type` | `EventType` |
| `atMs` | 판정이 확정된 신호의 시각 |
| `intensity` | 0 이상 1 이하. 가속도 이벤트만 의미가 있고 나머지는 0 |
| `direction` | `Direction4?`. 가속도 이벤트와 차선 변경 이벤트에서 의미가 있음 |
| `scoreDelta` | 이 이벤트의 점수 변화(가감점 표 적용 전 원값) |

규칙: `COLLISION` 과 `PROXIMITY` 경고는 모두 `COLLISION_RISK` 로 판정한다.

### Trip (FR-013, FR-014)

| 필드 | 설명 |
|-|-|
| `id` | 문자열 |
| `startedAtMs` | 시작 시각 |
| `endedAtMs` | 종료 시각. 진행 중이면 없음 |
| `events` | `DrivingEvent` 목록(감정이 바뀌지 않은 것 포함, 시간순) |
| `score` | 점수 계산 결과 (아래) |

메모리에만 있다. 위치 값은 담지 않는다.

### 점수 (FR-014)

- 시작 100. 이벤트 `scoreDelta` 를 모두 더하고, **조회할 때만** 0~100 으로 자른다.
- 가감점: `LANE_DEPARTURE` −5, `COLLISION_RISK` −5, `NO_SIGNAL_LANE_CHANGE` −3, `HARD_ACCEL`·`HARD_BRAKE`·`HARD_CORNER` 각 −2, `GOOD_LANE_CHANGE` +1(주행당 가점 합 +5 상한), `SEATBELT_OK` +2.
- 상한은 `GOOD_LANE_CHANGE` 에만 적용한다. 상한을 넘은 `GOOD_LANE_CHANGE` 는 이벤트로 기록되고 `scoreDelta` 는 0 이다.

## 감정 상태 [E]

### Mood (FR-009)

| Mood | 우선순위 | 들어오는 이벤트 | 지속 시간(초안) |
|-|-|-|-|
| `IDLE` | 0 | 기본 | - |
| `AMBIENT` | 1 | (이 기능에서는 입력 없음. 공조, 와이퍼, 매너 모드용 자리) | 조건이 유지되는 동안 |
| `HAPPY` | 2 | `SEATBELT_OK`, `GOOD_LANE_CHANGE`, `TRIP_END`(점수 80 이상) | 2000ms |
| `SQUASHED` | 3 | `HARD_ACCEL`, `HARD_BRAKE`, `HARD_CORNER` | 1200ms |
| `DIZZY` | 4 | `LANE_DEPARTURE` | 3000ms |
| `STARTLED` | 5 | `COLLISION_RISK` | 3000ms |

### PetState

`mood`, `intensity`(0~1), `direction`(`Direction4?`), `untilMs`(이 시각에 `IDLE` 또는 배경 상태로 복귀).

### 전이 규칙 (FR-011, FR-012)

1. 새 이벤트의 우선순위가 현재 이상이면 즉시 전환하고 `untilMs` 를 다시 계산한다.
2. 낮으면 버린다. 대기열에 쌓지 않는다.
3. `untilMs` 가 지나면 배경 상태(있으면) 또는 `IDLE` 로 복귀한다. 시간은 신호 시각 또는 `advanceTo` 로만 진행한다.
4. `NO_SIGNAL_LANE_CHANGE` 는 감정을 바꾸지 않는다.
5. 모든 이벤트는 감정 전환 여부와 관계없이 Trip 에 기록한다.

## 출력 [E]

### MotionTier (FR-015, FR-016)

`STATIC`(정적), `ONE_SHOT`(일회성 짧은 전환), `FULL`(풀 모션).

| 제한 상태 | 설정 `allowOneShot` | 출력 등급 |
|-|-|-|
| `NONE` | - | `FULL` |
| `ACTIVE` | `false`(기본) | `STATIC` |
| `ACTIVE` | `true` | `STATIC` 또는 `ONE_SHOT`(이 기능에서는 `ONE_SHOT` 을 출력할 수 있는 경로만 준비하고 기본 꺼짐) |
| `UNKNOWN` | - | `STATIC` (항상. `ACTIVE` 와 같게 취급) |

초기 제한 상태는 `UNKNOWN` 이다(입력이 오기 전까지 `STATIC`).

### PetOutput (FR-018, FR-019)

`mood`, `intensity`, `direction`, `tier`, `turnSignal`(`TurnState`), `atMs`.
이벤트가 없어도 제한 상태나 방향지시등이 바뀌면 새 값이 나간다.

## 설정 [E]

### EngineConfig (FR-002, FR-007, FR-010, FR-014, FR-016, FR-023)

한 곳에 모아 테스트에서 바꿀 수 있다. 값은 모두 초안이다(개발 명세서 3~4장, 스펙).

| 항목 | 값 |
|-|-|
| `hardAccelThresholdMps2` | 2.9 |
| `hardBrakeThresholdMps2` | 3.9 |
| `hardCornerThresholdMps2` | 3.9 |
| `cornerMinSpeedMps` | 15 km/h = 4.1667 |
| `intensityMaxMps2` (`A_MAX`) | 7.8 `[확인 필요]` |
| `smoothingAlpha` | 0.4 |
| `confirmSamples` | 2 |
| `maxSampleGapMs` | 2000 |
| `cooldownHardMs` | 3000 |
| `cooldownWarningMs` | 10000 |
| `cooldownLaneEvalMs` | 5000 |
| `laneChangeLookbackMs` | 3000 |
| 감정 지속 시간 | 깜짝 3000, 어지러움 3000, 찌그러짐 1200, 기쁨 2000 |
| 점수 가중치와 `goodLaneChangeBonusCap`(5) | 위 점수 표 |
| `happyScoreThreshold` | 80 |
| `allowOneShot` | `false` |

## 상태 전이 요약

```text
Trip:  UNKNOWN --(ParkedState false)--> IN_TRIP        (이벤트 없음, 앱이 주행 중에 켜진 경우)
       PARKED  --(ParkedState false)--> IN_TRIP        (TRIP_START)
       IN_TRIP --(ParkedState true)---> ARRIVING --(Speed == 0)--> PARKED   (TRIP_END)

Mood:  IDLE --이벤트--> 해당 감정 --(untilMs 경과)--> IDLE
       (현재 감정 도중, 같거나 높은 우선순위만 끊고 들어옴. 낮으면 버림)
```
