# Implementation Plan: 주행 이벤트 판정과 펫 감정 결정 코어

**Branch**: `001-driving-event-core` | **Date**: 2026-11-08 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-driving-event-core/spec.md`

## Summary

차량 신호(속도, 방향, 주차 상태, 브레이크, 방향지시등, 경고, 차선 변경, 주행 제한 상태)를 하나의 시간순 스트림으로 받아,
급가속·급제동·급커브·경고·차선 변경 이벤트를 판정하고, 펫 감정 상태와 주행 점수를 정하고, 주행 제한 상태에 따라
모션 등급(정적/일회성/풀)을 출력하는 순수 로직 코어를 만든다.

접근 방식: 신호 입력 타입과 소스 인터페이스, 목업 시나리오는 `:signal` 에, 판정과 상태 머신과 점수와 게이트는 `:engine` 에 둔다.
엔진은 시계를 읽지 않고 신호의 타임스탬프만으로 움직여서 결정적이다(같은 입력 → 같은 출력). 핵심 로직은 코루틴 없이 동기 함수로
만들어 JVM 단위 테스트로 검증하고, 코루틴 래퍼는 얇게 둔다. 이 기능은 SDK 의존성과 권한을 추가하지 않는다.

## Technical Context

**Language/Version**: Kotlin 2.2.10 (기존), JVM target 11

**Primary Dependencies**: kotlinx-coroutines-core 1.10.2 (기존), kotlinx-coroutines-test 1.10.2 (테스트용으로 추가). 새 런타임 의존성 없음.

**Storage**: N/A. 주행 기록(Trip)은 메모리에만 둔다. 영속화는 별도 기능이다.

**Testing**: JUnit4 (기존) + kotlinx-coroutines-test. 실행: `./gradlew :signal:testDebugUnitTest :engine:testDebugUnitTest` (`JAVA_HOME` 은 Android Studio JBR).

**Target Platform**: Android 라이브러리 모듈(`:signal`, `:engine`), compile/targetSdk 34, minSdk 29. 로직 코드는 Android API 를 쓰지 않는다.

**Project Type**: 기존 멀티 모듈 Android 앱의 라이브러리 모듈 확장(모바일 앱 구조 변경 없음).

**Performance Goals**: 신호 1건당 O(1) 처리(10Hz 스트림에서 무시할 수 있는 비용). 데모 시나리오 전체 판정 테스트 1초 이내, 두 모듈 전체 테스트 10초 이내(SC-005).

**Constraints**: 결정적 동작(벽시계 금지), 모든 임계값은 `EngineConfig` 한 곳, 엔진은 신호 출처를 모른다(FR-022), SDK 의존성 추가 금지(헌장 II).

**Scale/Scope**: 이벤트 10종, 감정 6종, 모션 등급 3종, 신호 타입 9종. 주행 한 번에 이벤트 수십 건.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 원칙 | 점검 | 결과 |
|-|-|-|
| I. 주행 안전 우선 | `MotionGate` 가 제한 없음 → 풀, 제한 있음/알 수 없음 → 정적을 출력. 일회성 등급은 설정 기본 꺼짐. 제한 상태 변경은 다음 출력에 즉시 반영(FR-015~017, SC-003) | PASS |
| II. 신호 소스 추상화와 모듈 경계 | 입력은 `VehicleSignalSource` 로만. `:engine` 은 `:signal` 의 타입에만 의존(app → pet → engine → signal 유지). SDK 호출 없음 | PASS |
| III. 테스트 우선 | 모든 판정 규칙을 JVM 단위 테스트로 먼저 작성. 데모 시나리오(이벤트 7개, 94점)를 회귀 테스트로 고정. 임계값은 `EngineConfig` 에 모아 주입 | PASS |
| IV. 플랫폼 접근과 권한 최소화 | 이 기능은 권한과 SDK 를 추가하지 않는다. 속도, 방위각 등 실제 출처는 별도 기능 | PASS |
| V. 비밀값과 개인정보 | 위치를 저장하지 않는다. Trip 에는 이벤트 종류, 시각, 세기, 점수만 둔다 | PASS |
| VI. 근거 없는 값 금지 | 임계값은 개발 명세서의 초안 값과 출처를 표시. 세기 정규화 상한과 방위각 부호 규칙은 `[확인 필요]` 로 `research.md` 에 기록 | PASS (미결 2건 명시) |

복잡도 위반 없음. Complexity Tracking 은 비워 둔다.

## Project Structure

### Documentation (this feature)

```text
specs/001-driving-event-core/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
│   ├── signal-source.md
│   └── engine-api.md
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
signal/src/main/java/com/example/dashpet/signal/
├── RawSignal.kt                 # 신호 sealed 타입과 enum (TurnState, WarningType, Side, RestrictionState)
├── VehicleSignalSource.kt       # 소스 인터페이스
├── MockSignalSource.kt          # 시나리오 재생 소스
└── scenario/
    ├── Scenario.kt              # 시나리오 모델과 expand()(10Hz 풀기)
    ├── ScenarioBuilder.kt       # 시나리오를 코드로 쓰는 DSL
    └── DemoScenarios.kt         # demo-commute (개발 명세서 9장)

signal/src/test/java/com/example/dashpet/signal/
├── ScenarioExpandTest.kt
└── MockSignalSourceTest.kt

engine/src/main/java/com/example/dashpet/engine/
├── EngineConfig.kt              # 모든 임계값, 간격, 지속 시간, 가중치
├── DrivingEvent.kt              # EventType, Direction4, DrivingEvent
├── Kinematics.kt                # 평활화, 앞뒤/옆 가속도 계산, 샘플 버림
├── EventDetector.kt             # 가속도 이벤트, 경고, 차선 변경 판정과 재발 간격
├── TripTracker.kt               # 주행 시작/종료 수명주기, Trip 기록
├── ScoreKeeper.kt               # 가감점, 가점 상한, 마지막에만 0~100 자름
├── Mood.kt                      # Mood, PetState
├── MoodMachine.kt               # 우선순위 전이, 지속 시간, 복귀
├── MotionGate.kt                # 제한 상태 → 모션 등급
├── PetOutput.kt                 # 엔진 출력
├── EngineCore.kt                # 위 구성요소를 묶는 동기 코어
└── DrivingEngine.kt             # Flow 연결 래퍼 (StateFlow/SharedFlow)

engine/src/test/java/com/example/dashpet/engine/
├── KinematicsTest.kt
├── EventDetectorTest.kt
├── MoodMachineTest.kt
├── MotionGateTest.kt
├── ScoreKeeperTest.kt
├── TripTrackerTest.kt
├── EngineCoreTest.kt
├── DemoScenarioTest.kt          # SC-001, SC-002
└── DrivingEngineTest.kt         # coroutines-test
```

**Structure Decision**: 기존 `:signal` 과 `:engine` 모듈을 그대로 쓴다(새 모듈 없음). `:engine` 은 `:signal` 을 `api` 로 의존하므로
테스트에서 `DemoScenarios` 를 바로 쓸 수 있다. 각 모듈의 `ModulePlaceholder.kt` 와 `SmokeTest.kt` 는 실제 파일로 교체하면서 삭제한다.
`:pet` 과 `:app` 은 이 기능에서 변경하지 않는다.

## Complexity Tracking

위반 없음.

## Post-Design Constitution Re-check

Phase 1 설계(data-model, contracts)를 마친 뒤 다시 점검했다. 결과는 모두 PASS 이다.
- 엔진 공개 API(`contracts/engine-api.md`)는 `:signal` 타입만 받고 SDK 타입을 노출하지 않는다(원칙 II).
- 시간을 입력으로만 받으므로(`advanceTo`) 결정적이고 테스트 가능하다(원칙 III).
- `PetOutput` 에는 위치 값이 없다(원칙 V).
- 미결 항목은 `research.md` 의 "미결 항목"에 모았다(원칙 VI).
