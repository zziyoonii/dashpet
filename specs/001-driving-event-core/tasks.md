---

description: "주행 이벤트 판정과 펫 감정 결정 코어 구현 작업 목록"
---

# Tasks: 주행 이벤트 판정과 펫 감정 결정 코어

**Input**: `specs/001-driving-event-core/` 의 `plan.md`, `spec.md`, `data-model.md`, `contracts/`, `research.md`, `quickstart.md`

**Prerequisites**: plan.md, spec.md (완료)

**Tests**: 이 기능은 **테스트를 먼저 쓴다**(헌장 III, 비협상). 각 스토리는 테스트 작업이 구현 작업보다 앞에 있고, 구현 전에 테스트가 실패(또는 컴파일 실패)하는 것을 확인한다.

**Organization**: 스토리별로 묶었다. 각 스토리는 독립적으로 테스트할 수 있다.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 다른 파일이고 아직 끝나지 않은 작업에 의존하지 않아 병렬로 할 수 있음
- **[Story]**: 스펙의 사용자 스토리(US1~US6)

## Path Conventions

- 소스: `signal/src/main/java/com/example/dashpet/signal/`, `engine/src/main/java/com/example/dashpet/engine/`
- 테스트: `signal/src/test/java/com/example/dashpet/signal/`, `engine/src/test/java/com/example/dashpet/engine/`
- 아래 경로의 `signal/...`, `engine/...` 는 위 두 디렉터리를 줄여 쓴 것이다(패키지 경로 `com/example/dashpet/<모듈>/` 포함).
- 실행 환경: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"` 후 `./gradlew :signal:testDebugUnitTest :engine:testDebugUnitTest --console=plain`

**plan.md 와의 차이**: 스토리마다 같은 파일을 고치지 않도록 plan.md 의 `EventDetector.kt` 를 `AccelEventDetector.kt`(US1), `WarningEventDetector.kt`(US2), `LaneChangeEventDetector.kt`(US4) 로, 테스트 `EngineCoreTest.kt` 를 스토리별 파일로 나눴다(plan.md 구조 목록도 같이 갱신).

---

## Phase 1: Setup (공통 설정)

**Purpose**: 빌드 의존성 정리

- [ ] T001 `gradle/libs.versions.toml` 에 `kotlinx-coroutines-test` 라이브러리를 추가한다(`group = "org.jetbrains.kotlinx"`, `name = "kotlinx-coroutines-test"`, `version.ref = "coroutines"`).
- [ ] T002 [P] `signal/build.gradle.kts` 에서 `implementation(libs.kotlinx.coroutines.core)` 를 `api(libs.kotlinx.coroutines.core)` 로 바꾸고(공개 인터페이스에 `Flow` 를 노출하므로) `testImplementation(libs.kotlinx.coroutines.test)` 를 추가한다.
- [ ] T003 [P] `engine/build.gradle.kts` 에서 `implementation(libs.kotlinx.coroutines.core)` 를 `api(...)` 로 바꾸고(`DrivingEngine` 이 `StateFlow` 를 노출) `testImplementation(libs.kotlinx.coroutines.test)` 를 추가한다.

---

## Phase 2: Foundational (모든 스토리의 선행 조건)

**Purpose**: 모든 스토리가 쓰는 신호 타입, 설정, 값 타입, 시나리오 DSL(테스트 도구)

**⚠️ CRITICAL**: 이 단계가 끝나기 전에는 스토리 작업을 시작하지 않는다.

- [ ] T004 [P] `signal/src/test/.../RawSignalTest.kt` 에 검증 테스트를 쓴다: `Speed` 는 "0 이상. 음수는 거부", `Heading` 은 "0 이상 360 미만(나침반, 시계 방향이 증가). 범위 밖은 360 으로 나눈 나머지로 정규화"(예: 360 → 0, 370 → 10, -10 → 350), 모든 신호는 `timestampMs` 를 가진다.
- [ ] T005 `signal/src/main/.../RawSignal.kt` 에 sealed 타입 `RawSignal`(`Speed`, `Heading`, `ParkedState`, `BrakePedal`, `TurnSignal`, `VehicleWarning`, `LaneChange`, `Restriction`)과 열거형을 만든다: `TurnState`(`OFF`, `LEFT`, `RIGHT`), `WarningType`(`SEATBELT`, `LANE_DEPARTURE`, `COLLISION`, `PROXIMITY`), `Side`(`LEFT`, `RIGHT`), `RestrictionState`(`NONE`, `ACTIVE`, `UNKNOWN`). T004 의 검증 규칙을 만족시킨다.
- [ ] T006 [P] `signal/src/main/.../VehicleSignalSource.kt` 에 `contracts/signal-source.md` 의 인터페이스(`val signals: Flow<RawSignal>`, `start()`, `stop()`)를 만든다.
- [ ] T007 [P] `signal/src/test/.../scenario/ScenarioExpandTest.kt` 에 테스트를 쓴다: `speedRamp` 와 `headingTurn` 이 100ms 간격 신호로 풀림, 같은 시각 신호의 입력 순서 유지, 같은 시나리오를 두 번 `expand()` 해도 같은 결과, 검증("단계 시각은 감소하지 않음", "`durationMs` 는 양수")이 위반되면 거부.
- [ ] T008 `signal/src/main/.../scenario/Scenario.kt` 에 `Scenario(name, steps)` 와 순수 함수 `expand(): List<RawSignal>` 를 만든다(T005, T007 의존). `contracts/signal-source.md` 의 단계 종류를 따른다.
- [ ] T009 `signal/src/main/.../scenario/ScenarioBuilder.kt` 에 DSL(`scenario(name) { at(ms) { parked(..), brakePedal(..), turnSignal(..), warning(type, active), laneChange(side), restriction(state), speed(mps), heading(deg), speedRamp(fromKmh, toKmh, durationMs), headingTurn(deltaDeg, durationMs) } }`)을 만든다(T008 의존).
- [ ] T010 [P] `engine/src/main/.../EngineConfig.kt` 에 `data-model.md` 의 설정 표를 그대로 기본값으로 둔 데이터 클래스를 만든다: `hardAccelThresholdMps2 = 2.9`, `hardBrakeThresholdMps2 = 3.9`, `hardCornerThresholdMps2 = 3.9`, `cornerMinSpeedMps = 15 / 3.6`, `intensityMaxMps2 = 7.8`(`[확인 필요]` 주석), `smoothingAlpha = 0.4`, `confirmSamples = 2`, `maxSampleGapMs = 2000`, `cooldownHardMs = 3000`, `cooldownWarningMs = 10000`, `cooldownLaneEvalMs = 5000`, `laneChangeLookbackMs = 3000`, 감정 지속 시간(깜짝 3000, 어지러움 3000, 찌그러짐 1200, 기쁨 2000), 점수 가중치와 `goodLaneChangeBonusCap = 5`, `happyScoreThreshold = 80`, `allowOneShot = false`.
- [ ] T011 [P] `engine/src/main/.../DrivingEvent.kt` 에 `EventType`(10종: `TRIP_START`, `SEATBELT_OK`, `HARD_ACCEL`, `HARD_BRAKE`, `HARD_CORNER`, `LANE_DEPARTURE`, `COLLISION_RISK`, `GOOD_LANE_CHANGE`, `NO_SIGNAL_LANE_CHANGE`, `TRIP_END`), `Direction4`(`FRONT`, `BACK`, `LEFT`, `RIGHT`), `DrivingEvent(type, atMs, intensity, direction, scoreDelta)` 를 만든다. `intensity` 는 "0 이상 1 이하"로 제한한다.
- [ ] T012 [P] `engine/src/main/.../Mood.kt` 에 `Mood`(`IDLE` 0, `AMBIENT` 1, `HAPPY` 2, `SQUASHED` 3, `DIZZY` 4, `STARTLED` 5 우선순위)와 `PetState(mood, intensity, direction, untilMs)` 를 만든다.
- [ ] T013 `engine/src/main/.../PetOutput.kt` 에 `MotionTier`(`STATIC`, `ONE_SHOT`, `FULL`)와 `PetOutput(mood, intensity, direction, tier, turnSignal, atMs)` 를 만든다(T005, T011, T012 의존).
- [ ] T014 자리 표시 파일을 지운다: `signal/src/main/.../ModulePlaceholder.kt`, `engine/src/main/.../ModulePlaceholder.kt`, `engine/src/test/.../SmokeTest.kt`(T005, T011 이후).
- [ ] T015 `./gradlew :signal:testDebugUnitTest :engine:compileDebugKotlin` 로 Foundational 이 통과하는지 확인한다.

**Checkpoint**: 신호 타입, 시나리오 DSL, 설정, 값 타입이 준비됨. 스토리 작업 시작 가능.

---

## Phase 3: User Story 1 - 급가속·급제동·급커브에 펫이 반응한다 (Priority: P1) 🎯 MVP

**Goal**: 속도와 진행 방향 신호에서 급가속, 급제동, 급커브를 정확히 판정하고 세기(0~1)와 방향을 낸다.

**Independent Test**: 속도, 방향, 브레이크 페달 신호만 넣어서 이벤트 목록을 확인한다(US1 시나리오 1~4).

### Tests for User Story 1 ⚠️ (구현 전에 먼저 쓰고 실패를 확인)

- [ ] T016 [P] [US1] `engine/src/test/.../KinematicsTest.kt`: 앞뒤 가속도는 `(v_t − v_{t−1}) / Δt`, 옆 가속도는 방위 변화율(−180~180° 로 정규화한 각도 차를 라디안으로 변환) × 최신 속도, 359°→1° 는 +2°, 속도와 방향에 지수이동평균(가중 0.4)을 적용(방향은 직전 값 기준으로 펼친 뒤 평활화), "신호 간격이 2초를 넘거나 속도 정보가 없는 샘플은 버리고" 기준선도 초기화.
- [ ] T017 [P] [US1] `engine/src/test/.../AccelEventDetectorTest.kt`: 50km/h 에서 2초 만에 0km/h 감속 + 페달 눌림 → `HARD_BRAKE` 1회(방향 `FRONT`, 세기 0~1), 같은 감속인데 페달이 안 눌렸으면 이벤트 없음, 50km/h 에서 1초 동안 방위 +30° → `HARD_CORNER` 1회(우회전이므로 방향 `LEFT`), 방위 −30° → 방향 `RIGHT`, 8초 동안 0→50km/h 가속(약 1.7 m/s²)은 이벤트 없음(SC-004), 15km/h 미만의 큰 방향 변화는 `HARD_CORNER` 없음, 임계값을 한 샘플만 넘으면 이벤트 없음("2회 연속"), 재발 간격 3초 안의 같은 종류는 한 번만, 세기는 `clamp((|a| − 임계값) / (intensityMaxMps2 − 임계값), 0, 1)`, 임계값을 `EngineConfig` 로 바꾸면 판정이 바뀜.
- [ ] T018 [P] [US1] `engine/src/test/.../Fixtures.kt`: 시나리오를 `expand()` 해서 `EngineCore` 에 순서대로 넣고 `EngineStep` 목록을 모으는 헬퍼, 종류로 이벤트를 찾는 헬퍼.
- [ ] T019 [US1] `engine/src/test/.../EngineCoreAccelTest.kt`: 시나리오 DSL(`speedRamp`, `headingTurn`, `brakePedal`)로 US1 시나리오 1~4 를 `EngineCore` 수준에서 확인(T018 의존).

### Implementation for User Story 1

- [ ] T020 [US1] `engine/src/main/.../Kinematics.kt`: 평활화, 앞뒤/옆 가속도 계산, 샘플 버림(T016 을 통과시킴).
- [ ] T021 [US1] `engine/src/main/.../AccelEventDetector.kt`: 임계값 판정, 2회 연속 확정, 재발 간격 3초, 세기, 방향(급제동 `FRONT`, 급가속 `BACK`, 급커브는 우회전 → `LEFT`, 좌회전 → `RIGHT`), 급제동은 페달 눌림 확인(T017 을 통과시킴, T020 의존).
- [ ] T022 [US1] `engine/src/main/.../EngineCore.kt`: `process(signal): EngineStep`, `advanceTo(timeMs): EngineStep`, `currentTrip()`, `completedTrips()` 의 틀과 `EngineStep(events, output)`. 이 단계에서는 `Speed`, `Heading`, `BrakePedal` 을 처리해 가속도 이벤트를 내고, "시간이 직전보다 작은 신호는 무시"하며 예외를 던지지 않는다(`contracts/engine-api.md`). T019 를 통과시킴(T021 의존).

**Checkpoint**: US1 이 단독으로 동작하고 테스트가 통과한다. (프로토타입 ② 물리 반응의 판정 코어)

---

## Phase 4: User Story 2 - 차량 경고를 펫의 감정으로 번역한다 (Priority: P1)

**Goal**: 차선 이탈, 충돌 위험 경고가 어지러움, 깜짝으로 번역되고, 우선순위 규칙대로 전환·복귀한다.

**Independent Test**: 경고 신호와 이벤트만 넣어 감정 변화를 확인한다(US2 시나리오 1~4).

### Tests for User Story 2 ⚠️

- [ ] T023 [P] [US2] `engine/src/test/.../MoodMachineTest.kt`: 차선 이탈 → `DIZZY` 3초 뒤 `IDLE`, 어지러움 중 충돌 위험 → 즉시 `STARTLED`, 깜짝 중 급제동(낮은 우선순위)은 버려지고 대기열에 쌓이지 않음, 같은 우선순위는 즉시 전환하고 지속 시간 재시작, 지속 시간(`STARTLED` 3000, `DIZZY` 3000, `SQUASHED` 1200, `HAPPY` 2000), 이벤트→감정 표(`HARD_*` → `SQUASHED`, `LANE_DEPARTURE` → `DIZZY`, `COLLISION_RISK` → `STARTLED`, `SEATBELT_OK`·`GOOD_LANE_CHANGE`·`TRIP_END`(점수 80 이상) → `HAPPY`), `NO_SIGNAL_LANE_CHANGE` 는 감정을 바꾸지 않음, `advanceTo` 로만 복귀, 시간은 되돌리지 않음.
- [ ] T024 [P] [US2] `engine/src/test/.../WarningEventDetectorTest.kt`: `LANE_DEPARTURE` 경고가 켜질 때(`active → true`) `LANE_DEPARTURE` 이벤트, `COLLISION` 과 `PROXIMITY` 모두 `COLLISION_RISK` 로 판정, 재발 간격 10초 안에서는 한 번만, 경고가 꺼질 때는 이벤트 없음, `SEATBELT` 경고는 이 감지기가 다루지 않음.
- [ ] T025 [P] [US2] `engine/src/test/.../EngineCoreMoodTest.kt`: 경고 신호 → 출력의 `mood`, `advanceTo` 로 3초 뒤 `IDLE` 로 복귀, 감정 도중 낮은 우선순위 이벤트가 버려짐(US2 시나리오 1~4).

### Implementation for User Story 2

- [ ] T026 [P] [US2] `engine/src/main/.../MoodMachine.kt`: 우선순위 전이, 지속 시간, 복귀, 이벤트→감정 표(T023 을 통과시킴).
- [ ] T027 [P] [US2] `engine/src/main/.../WarningEventDetector.kt`: 경고 켜짐 판정, 종류 매핑, 재발 간격 10초(T024 를 통과시킴).
- [ ] T028 [US2] `engine/src/main/.../EngineCore.kt` 에 `MoodMachine` 과 `WarningEventDetector` 를 연결하고 출력의 `mood`, `intensity`, `direction` 을 채운다. T025 를 통과시킴(T026, T027 의존).

**Checkpoint**: US1 + US2 가 함께 동작한다. (프로토타입 ③ 경고 번역의 판정 코어)

---

## Phase 5: User Story 3 - 주행 제한 상태에 따라 표현 수위를 낮춘다 (Priority: P1)

**Goal**: 제한 상태(없음/있음/알 수 없음)에 따라 모션 등급을 정하고, 제한 중에는 풀 모션이 절대 나오지 않는다.

**Independent Test**: 제한 상태를 바꿔 가며 같은 이벤트를 넣고 등급을 확인한다(US3 시나리오 1~5).

### Tests for User Story 3 ⚠️

- [ ] T029 [P] [US3] `engine/src/test/.../MotionGateTest.kt`: `NONE` → `FULL`, `ACTIVE` → `STATIC`, `UNKNOWN` → `STATIC`, `allowOneShot = false`(기본)이면 `ONE_SHOT` 은 어떤 경우에도 나오지 않음, `allowOneShot = true` 일 때 `ACTIVE` 에서 `ONE_SHOT` 을 낼 수 있는 경로가 있음(`UNKNOWN` 은 항상 `STATIC`).
- [ ] T030 [P] [US3] `engine/src/test/.../EngineCoreMotionTest.kt`: 초기 제한 상태는 `UNKNOWN`(입력 전에는 `STATIC`), `Restriction` 신호가 오면 이벤트 없이도 새 출력이 나옴, 풀 모션 중 `ACTIVE` 가 되면 그 다음 출력부터 `STATIC`, **모든 `EventType` 을 `ACTIVE` 와 `UNKNOWN` 에서 발생시켜도 `FULL` 이 출력되지 않음**(SC-003, 반복 검증).

### Implementation for User Story 3

- [ ] T031 [US3] `engine/src/main/.../MotionGate.kt`: 제한 상태와 `allowOneShot` → `MotionTier`(T029 를 통과시킴).
- [ ] T032 [US3] `engine/src/main/.../EngineCore.kt` 에서 `Restriction` 신호를 처리하고 매 출력의 `tier` 를 `MotionGate` 로 정한다. T030 을 통과시킴(T031 의존).

**Checkpoint**: P1 스토리 3개(US1~US3)가 모두 동작한다. 11/15 프로토타입의 판정 코어가 갖춰진다.

---

## Phase 6: User Story 4 - 주행 점수와 기록을 남긴다 (Priority: P2)

**Goal**: 주행 수명주기(출발, 벨트 체결, 도착), 차선 변경 평가, 점수, 주행 기록을 만든다.

**Independent Test**: 이벤트 목록과 신호로 최종 점수와 기록을 확인한다(US4 시나리오 1~4).

### Tests for User Story 4 ⚠️

- [ ] T033 [P] [US4] `engine/src/test/.../ScoreKeeperTest.kt`: 100 + 2 + 1 − 2 − 5 − 2 = 94, 가점(`GOOD_LANE_CHANGE`)은 "주행당 가점 합 +5 상한"이고 상한을 넘으면 이벤트는 기록되고 `scoreDelta` 는 0, 합산 중에는 자르지 않고 **조회할 때만** 0~100 으로 자름(예: −100 이하 합 → 0), 가감점 표(`LANE_DEPARTURE` −5, `COLLISION_RISK` −5, `NO_SIGNAL_LANE_CHANGE` −3, `HARD_ACCEL`·`HARD_BRAKE`·`HARD_CORNER` 각 −2, `GOOD_LANE_CHANGE` +1, `SEATBELT_OK` +2).
- [ ] T034 [P] [US4] `engine/src/test/.../TripTrackerTest.kt`: `ParkedState` 가 주차 → 주차 아님이면 `TRIP_START`, 초기 "알 수 없음"에서 첫 입력이 "주차 아님"이면 이벤트 없이 주행 시작(출발과 벨트 체결 이벤트 없음), 출발 후 벨트 경고가 켜졌다 꺼질 때만 `SEATBELT_OK`(출발 전 해제는 체결로 치지 않음), 주차 아님 → 주차이면서 속도 0 이 되면 `TRIP_END`("주차 상태여도 속도가 0이 아니면 도착으로 치지 않는다"), 주행이 없을 때 들어온 급가속 등은 무시, 모든 이벤트(감정이 안 바뀌는 것 포함)가 Trip 에 시각, 종류, 세기, 점수 변화와 함께 기록됨.
- [ ] T035 [P] [US4] `engine/src/test/.../LaneChangeEventDetectorTest.kt`: 차선 변경 시점 직전 3초 안에 같은 방향 방향지시등이 켜져 있었으면 `GOOD_LANE_CHANGE`, 아니면(다른 방향 포함) `NO_SIGNAL_LANE_CHANGE`, 재발 간격 5초, 차선 변경 직후 방향지시등이 꺼져도(데모: 32초 변경, 33초 꺼짐) 좋은 차선 변경.
- [ ] T036 [P] [US4] `engine/src/test/.../EngineCoreTripTest.kt`: 깜빡이 없는 차선 변경은 감정이 안 바뀌고 −3점과 기록만 남음, `TRIP_END` 시점 점수가 80 이상이면 `HAPPY`, `currentTrip()` 과 `completedTrips()` 동작(US4 시나리오 1~4).

### Implementation for User Story 4

- [ ] T037 [P] [US4] `engine/src/main/.../ScoreKeeper.kt`: 점수 계산(T033 을 통과시킴).
- [ ] T038 [P] [US4] `engine/src/main/.../LaneChangeEventDetector.kt`: 방향지시등 이력으로 차선 변경 평가(T035 를 통과시킴).
- [ ] T039 [US4] `engine/src/main/.../TripTracker.kt`: `Trip`(id, startedAtMs, endedAtMs, events, score) 과 수명주기(UNKNOWN → IN_TRIP, PARKED → IN_TRIP, IN_TRIP → ARRIVING → PARKED). 위치 값은 담지 않는다(T034 를 통과시킴, T037 의존).
- [ ] T040 [US4] `engine/src/main/.../EngineCore.kt` 에 `TripTracker`, `ScoreKeeper`, `LaneChangeEventDetector` 를 연결하고 `currentTrip()`, `completedTrips()` 를 구현하며 `TRIP_END` 에서 `MoodMachine` 에 점수를 넘긴다. T036 을 통과시킴(T038, T039 의존).

**Checkpoint**: 이벤트, 점수, 기록이 모두 동작한다.

---

## Phase 7: User Story 5 - 목업 시나리오로 반복 검증하고 시연한다 (Priority: P2)

**Goal**: 목업 소스, 데모 시나리오, Flow 래퍼를 만들고 데모 출근 시나리오의 이벤트 순서와 94점을 고정한다.

**Independent Test**: 데모 출근 시나리오를 재생해 기대 이벤트 순서와 점수를 확인한다(US5 시나리오 1~3).

### Tests for User Story 5 ⚠️

- [ ] T041 [P] [US5] `signal/src/test/.../MockSignalSourceTest.kt`: `IMMEDIATE` 와 `REAL_TIME`(coroutines-test 가상 시간)이 같은 내용과 순서의 신호를 냄, `start()` 전에는 방출하지 않고 `stop()` 후 `start()` 는 처음부터 다시 시작, 같은 시나리오 → 같은 스트림.
- [ ] T042 [P] [US5] `engine/src/test/.../DemoScenarioTest.kt`: `DemoScenarios.demoCommute` 를 재생해 이벤트가 `TRIP_START`, `SEATBELT_OK`, `HARD_CORNER`, `GOOD_LANE_CHANGE`, `LANE_DEPARTURE`, `HARD_BRAKE`, `TRIP_END` 순서로 나오고 점수가 정확히 94(SC-001), 100번 반복해도 이벤트 순서, 감정 전환, 점수가 같음(SC-002), 가속 구간(0→50km/h, 8초)에서 `HARD_ACCEL` 이 없음, `HARD_CORNER` 의 방향은 별도 테스트로 `research.md` R5 규칙(우회전 → `LEFT`)을 확인(방위 부호는 `[확인 필요]`).
- [ ] T043 [P] [US5] `engine/src/test/.../DrivingEngineTest.kt`(coroutines-test): `StateFlow<PetOutput>` 이 값이 바뀔 때만 갱신, `SharedFlow<DrivingEvent>` 가 확정된 이벤트를 냄, `start()`/`stop()` 이 소스와 함께 동작.

### Implementation for User Story 5

- [ ] T044 [P] [US5] `signal/src/main/.../MockSignalSource.kt`: `MockSignalSource(scenario, pacing)`(`Pacing.REAL_TIME`, `Pacing.IMMEDIATE`)(T041 을 통과시킴).
- [ ] T045 [P] [US5] `signal/src/main/.../scenario/DemoScenarios.kt`: `demoCommute` 를 개발 명세서 9장대로 정의한다. 0초에 `parked(true)`, `speed(0)`, `heading(90)`, `brakePedal(false)`, `turnSignal(OFF)`, `restriction(NONE)`(이게 있어야 8초의 출발이 `TRIP_START` 가 됨), 8초에 `parked(false)`, 벨트 경고 켜짐, `restriction(ACTIVE)`, 10초에 벨트 경고 꺼짐, 12초에 0→50km/h 램프(8000ms), 22초에 `headingTurn(+30, 1000)`, 30초에 방향지시등 `RIGHT`, 32초에 `laneChange(RIGHT)`, 33초에 방향지시등 `OFF`, 38초에 차선 이탈 경고 켜짐, 40초에 꺼짐, 46초에 `brakePedal(true)` 와 50→0km/h 램프(2000ms), 52초에 `parked(true)` 와 `restriction(NONE)`.
- [ ] T046 [US5] `engine/src/main/.../DrivingEngine.kt`: `DrivingEngine(source, scope, config)` 가 `EngineCore` 를 한 코루틴에서 순서대로 호출하고 `output`(`StateFlow`), `events`(`SharedFlow`), `start()`, `stop()` 을 제공한다(`contracts/engine-api.md`). T042, T043 을 통과시킴(T044, T045 의존).

**Checkpoint**: 데모 시나리오가 이벤트 7개와 94점을 안정적으로 재현한다. 11/15 영상용 목업 재생이 가능하다.

---

## Phase 8: User Story 6 - 방향지시등 상태를 펫에 전달한다 (Priority: P3)

**Goal**: 방향지시등 좌/우/꺼짐 상태를 이벤트와 별개로 출력에 싣는다.

**Independent Test**: 방향지시등 신호만 바꿔 넣고 출력의 `turnSignal` 을 확인한다(US6 시나리오 1~2).

### Tests for User Story 6 ⚠️

- [ ] T047 [P] [US6] `engine/src/test/.../EngineCoreTurnSignalTest.kt`: 방향지시등 `RIGHT` 가 켜지면 차선 변경이 없어도 출력의 `turnSignal` 이 `RIGHT`, `OFF` 가 되면 `OFF`, 변경 시 이벤트 없이도 새 출력이 나옴(FR-019).

### Implementation for User Story 6

- [ ] T048 [US6] `engine/src/main/.../EngineCore.kt` 에서 `TurnSignal` 신호를 출력의 `turnSignal` 에 반영한다. T047 을 통과시킴.

**Checkpoint**: 모든 스토리가 동작한다.

---

## Phase 9: Polish & Cross-Cutting Concerns

- [ ] T049 [P] `ARCHITECTURE.md` 를 갱신한다: `:signal` 의 신호 타입과 `:engine` 의 구성요소, 테스트 실행 명령.
- [ ] T050 [P] `specs/001-driving-event-core/checklists/traceability.md` 에 FR-001~FR-024 와 SC-001~SC-006 을 대응하는 테스트 이름으로 연결한 표를 만든다(빠진 항목이 있으면 테스트를 추가한다).
- [ ] T051 헌장 점검: `engine/src/main` 과 `signal/src/main` 에서 `System.currentTimeMillis`, `System.nanoTime`, `Date`, `Clock`, `Random` 을 쓰지 않았는지, `engine/src/main` 에 `ai.pleos` 와 `android.` import 가 없는지 `grep` 으로 확인한다.
- [ ] T052 `./gradlew assembleDebug :signal:testDebugUnitTest :engine:testDebugUnitTest --console=plain` 을 실행해 전체 통과와 테스트 시간 10초 이내(SC-005)를 확인한다.
- [ ] T053 `quickstart.md` 의 검증 시나리오 1~13 을 순서대로 확인하고, 테스트 파일 이름이 바뀐 곳은 문서를 고친다.
- [ ] T054 변경을 스토리 단위로 커밋한다(브랜치 `001-driving-event-core`). 푸시는 요청이 있을 때만 한다.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 바로 시작. T002, T003 은 T001 이후 서로 병렬.
- **Foundational (Phase 2)**: Setup 이후. 모든 스토리를 막는다.
- **User Stories (Phase 3~8)**: Foundational 이후. 같은 파일 `EngineCore.kt` 를 스토리마다 고치므로(T022, T028, T032, T040, T048) **우선순위 순서(US1 → US2 → US3 → US4 → US5 → US6)로 진행**한다. 테스트, 감지기, 상태 머신 등 다른 파일은 스토리 안에서 병렬로 할 수 있다.
- **Polish (Phase 9)**: 모든 스토리 이후.

### User Story Dependencies

- **US1 (P1)**: Foundational 이후 독립.
- **US2 (P1)**: Foundational 이후. `EngineCore.kt` 때문에 US1 뒤에 진행하지만 테스트와 감지기는 독립.
- **US3 (P1)**: Foundational 이후. 위와 같음.
- **US4 (P2)**: `MoodMachine`(US2)이 있어야 `TRIP_END` 감정 연결 가능. 나머지는 독립.
- **US5 (P2)**: US1~US4 가 있어야 데모 시나리오 테스트가 통과. `MockSignalSource` 자체는 Foundational 만 있으면 가능.
- **US6 (P3)**: Foundational 이후 독립(작음).

### Within Each User Story

- 테스트 → 실패(또는 컴파일 실패) 확인 → 구현 → 통과 확인 → 다음 스토리.
- 값 타입 → 감지기, 상태 머신 → `EngineCore` 연결.

### Parallel Opportunities

- Setup: T002 ∥ T003
- Foundational: T004 ∥ T006 ∥ T007 ∥ T010 ∥ T011 ∥ T012
- US1 테스트: T016 ∥ T017 ∥ T018
- US2: T023 ∥ T024 ∥ T025, 그리고 T026 ∥ T027
- US3 테스트: T029 ∥ T030
- US4 테스트: T033 ∥ T034 ∥ T035 ∥ T036, 구현: T037 ∥ T038
- US5: T041 ∥ T042 ∥ T043, 그리고 T044 ∥ T045

## Parallel Example: User Story 1

```text
# US1 테스트를 함께 쓴다:
Task: "KinematicsTest.kt (engine/src/test/.../KinematicsTest.kt)"
Task: "AccelEventDetectorTest.kt (engine/src/test/.../AccelEventDetectorTest.kt)"
Task: "Fixtures.kt (engine/src/test/.../Fixtures.kt)"
```

## Implementation Strategy

### 11/15 프로토타입 우선 (US1 → US2 → US3)

1. Phase 1~2 (Setup, Foundational)
2. Phase 3 US1 → **멈추고 검증**: 물리 반응 판정
3. Phase 4 US2 → 경고 번역 판정
4. Phase 5 US3 → 주행 제한에 따른 등급(안전 원칙)
5. Phase 7 US5 중 `MockSignalSource` 와 `DemoScenarios` 를 먼저 당겨서 영상용 재생을 확보하고 싶으면, US4(수명주기와 점수)를 먼저 끝낸 뒤 US5 를 진행한다(데모 시나리오 테스트가 `TRIP_START` 와 94점을 요구하므로).

### Incremental Delivery

각 스토리가 끝날 때마다 테스트를 통과시키고 커밋한다. 스토리를 건너뛰어도 앞 단계가 깨지지 않는다.

## Notes

- 모든 임계값은 `EngineConfig` 한 곳에서만 정의한다(FR-023). 테스트는 기본값을 따르되 필요하면 값을 바꿔 주입한다.
- 엔진은 시계를 읽지 않는다. 시간은 신호의 `timestampMs` 와 `advanceTo` 로만 진행한다(R6).
- `HARD_CORNER` 방향 규칙(R5)과 세기 상한 `intensityMaxMps2`(R4)는 `[확인 필요]` 이다. 코드와 테스트에 주석으로 표시한다.
- 같은 시각의 신호는 입력 순서를 유지한다. 데모 시나리오는 8초에 `parked(false)` 를 벨트 경고 켜짐보다 먼저 넣는다.
- 이 기능은 SDK 의존성과 `pleos.car.permission.*` 권한을 추가하지 않는다.
