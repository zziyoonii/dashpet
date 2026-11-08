# Implementation Plan: PetView (펫 캐릭터 그리기와 데모 재생 화면)

**Branch**: `002-pet-view` | **Date**: 2026-11-08 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/002-pet-view/spec.md`

## Summary

`:engine` 이 내보내는 출력(감정, 강도, 방향, 모션 등급, 방향지시등)과 이벤트를 받아 내 차 펫을 그린다. 캐릭터 시안(HTML)의 물리와
그리기 코드를 Compose Canvas 로 이식하되, 모션 등급이 정적이면 프레임 루프를 아예 돌리지 않고 움직임 없는 포즈와 색만 그린다.

접근 방식:
- `:pet` 에 순수 Kotlin 물리(`SoftBody`, `PetPhysics`)와 컨트롤러(`PetController`)를 두어 JVM 단위 테스트로 검증하고, 그리기(`PetPainter`)와
  Composable(`PetView`)은 얇게 만든다.
- 프레임 루프는 "모션 등급이 풀 모션이고 화면이 보일 때"만 돌린다. 정적 등급은 상태가 바뀔 때만 다시 그려서 화면 갱신 0회를 보장한다.
- `:app` 홈 화면에 데모 재생 컨트롤을 만든다(`DemoViewModel`).
- 001 기능에서 드러난 두 가지 빈틈을 작게 보완한다: 엔진 시간이 신호가 있어야만 진행되는 문제(`RawSignal.Tick`)와, 수동 제한 상태 전환이
  모션 등급 판단을 엔진 밖으로 끌어내지 않게 하는 `DrivingEngine.injectRestriction`.

## Technical Context

**Language/Version**: Kotlin 2.2.10 (기존), JVM target 11

**Primary Dependencies**: Jetpack Compose (BOM 2025.01.01 → Compose 1.7.8, material3 1.3.1), activity-compose 1.8.2, lifecycle 2.8.3(기존 해석 버전),
kotlinx-coroutines 1.10.2. 추가 후보: `:pet` 에 `androidx.compose.foundation`(Canvas 레이아웃), `androidx.lifecycle:lifecycle-runtime-compose`;
`:app` 에 `androidx.lifecycle:lifecycle-viewmodel-compose`(이미 transitive 로 들어오는지 확인 후 필요하면 추가). 새 외부 SDK 없음.

**Storage**: N/A. 데모 상태는 ViewModel 메모리에만 둔다.

**Testing**: JUnit4 + kotlinx-coroutines-test. 물리, 컨트롤러, 포즈, 루프 정책은 JVM 단위 테스트. 화면 모양은 시안과 나란히 비교하는
체크리스트(`checklists/visual-parity.md`)와 에뮬레이터 측정(`quickstart.md`)으로 검증한다(UI 스냅샷 테스트 도구는 도입하지 않음).

**Target Platform**: Android(AAOS, Pleos Connect), compile/targetSdk 34, minSdk 29. 로직은 Android API 없이 JVM 에서 테스트 가능해야 한다.

**Project Type**: 기존 멀티 모듈 Android 앱의 `:pet`(Compose 라이브러리)과 `:app` 확장. `:engine`, `:signal` 은 작은 가산 변경만.

**Performance Goals**: 풀 모션 목표 60fps(에뮬레이터 평균 55fps 이상), 프레임당 그리기 호출 약 250회 이하, 파티클 상한 60개. 정적 등급은 프레임 루프 없음.

**Constraints**: 정적 등급에서 반복 모션, 물리 계산, 파티클, 번짐, 전환 효과 금지(헌장 I). 풀 모션 CPU 25% 이하(주차·정지). 입력 후 0.25초 안에 반응.
SDK 의존성과 권한 추가 금지. 물리 시간은 프레임 `dt` 로만 진행해 테스트가 결정적이다.

**Scale/Scope**: 감정 9종(엔진 구동 5종 + 배경 4종), 방향 4종, 파티클 6종, 모듈 `:pet` 파일 약 11개, `:app` 파일 약 4개.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 원칙 | 점검 | 결과 |
|-|-|-|
| I. 주행 안전 우선 | 정적 등급은 프레임 루프 자체가 없고 물리, 파티클, 번짐이 꺼진다. 모션 등급 판단은 엔진 한 곳(`MotionGate`)에만 있다. 데모의 수동 제한 상태도 엔진에 신호로 주입한다(`injectRestriction`). 일회성 등급은 정적과 같게 처리 | PASS |
| II. 신호 소스 추상화와 모듈 경계 | `:pet` 은 `:engine` 의 `PetOutput`, `DrivingEvent`, `MotionTier` 만 쓰고 SDK, `:signal` 구현체를 직접 보지 않는다. 의존 방향(app → pet → engine → signal) 유지. 엔진 쪽 변경은 `:signal` 의 신호 타입을 쓰는 가산 변경이다 | PASS |
| III. 테스트 우선 | 물리, 컨트롤러, 포즈, 루프 정책은 테스트를 먼저 쓴다. 화면 모양은 JVM 에서 검증할 수 없어 시안과의 나란히 비교 체크리스트와 에뮬레이터 측정으로 대체하고 결과를 기록한다(헌장 III 마지막 항목) | PASS (시각 검증은 수동, 근거 명시) |
| IV. 플랫폼 접근과 권한 최소화 | 권한과 SDK 를 추가하지 않는다. 모션 줄이기 설정은 `ValueAnimator.areAnimatorsEnabled()` 로 읽는다(권한 불필요) | PASS |
| V. 비밀값과 개인정보 | 위치, 개인정보를 쓰지 않는다. 이름은 입력 문자열을 표시만 한다 | PASS |
| VI. 근거 없는 값 금지 | 물리 상수는 시안 코드에서 그대로 옮기고 출처를 표시한다. 시안에 없는 정적 포즈 값(기울기, 색 혼합)은 초안으로 두고 `[확인 필요]` 로 기록한다 | PASS (미결 4건 명시) |

## Project Structure

### Documentation (this feature)

```text
specs/002-pet-view/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
│   └── pet-view-api.md
├── checklists/
│   ├── requirements.md
│   └── visual-parity.md # 시안과 나란히 비교하는 승인 체크리스트
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
signal/src/main/java/com/example/dashpet/signal/
├── RawSignal.kt                 # Tick 신호 추가 (가산)
└── MockSignalSource.kt          # 시간 진행용 Tick 을 시나리오 시각에 맞춰 함께 방출 (가산)

engine/src/main/java/com/example/dashpet/engine/
├── EngineCore.kt                # Tick 을 시간 진행으로 처리 (가산)
└── DrivingEngine.kt             # injectRestriction 추가 (가산)

pet/src/main/java/com/example/dashpet/pet/
├── PetAppearance.kt             # PetAppearance, Personality, AmbientMood
├── PetPalette.kt                # 시안 팔레트와 상태 색
├── PetPhysicsConfig.kt          # 시안 상수를 한 곳에 (출처 표시)
├── SoftBody.kt                  # 22점 소프트바디 (순수 Kotlin)
├── PetPhysics.kt                # 좌우, 앞뒤, 점프 축과 반응 (순수 Kotlin)
├── Particles.kt                 # 파티클 풀(상한), 종류
├── PetPose.kt                   # 정적 포즈 계산 (순수 Kotlin)
├── MotionPolicy.kt              # 프레임 루프 실행 조건, 모션 줄이기 보정
├── PetController.kt             # PetOutput, DrivingEvent → 물리 명령, 정적/풀 전환
├── PetPainter.kt                # DrawScope 그리기 (몸, 눈, 눈썹, 입, 효과, 유리)
└── PetView.kt                   # Composable (프레임 루프, 생명주기, 정적/풀 분기), 엔진 연결 도우미

pet/src/test/java/com/example/dashpet/pet/
├── SoftBodyTest.kt
├── PetPhysicsTest.kt
├── ParticlesTest.kt
├── PetPoseTest.kt
├── MotionPolicyTest.kt
└── PetControllerTest.kt

app/src/main/java/com/example/dashpet/ui/
├── screens/HomeScreen.kt        # PetView + 데모 컨트롤 (placeholder 교체)
└── demo/
    ├── DemoViewModel.kt         # 엔진, 재생, 수동 제한 상태, 확인용 표시
    └── DemoControls.kt          # 재생/정지, 제한 상태 토글, 확인용 패널

app/src/test/java/com/example/dashpet/ui/demo/DemoViewModelTest.kt
engine/src/test/java/com/example/dashpet/engine/ (EngineCoreTickTest.kt, DrivingEngineTest.kt 보강)
signal/src/test/java/com/example/dashpet/signal/ (MockSignalSourceTest.kt 보강)
```

**Structure Decision**: `:pet` 에 물리와 그리기를 두고, 물리는 Compose 없이 테스트 가능한 순수 Kotlin 으로 분리한다. 화면 전체 배치와 데모는 `:app` 에 둔다.
`:engine`, `:signal` 은 새 파일 없이 가산 변경 3건만 한다(`Tick`, `Tick` 처리, `injectRestriction`). `:pet` 의 자리 표시 파일 `ModulePlaceholder.kt` 는 실제 파일로 교체하며 삭제한다.

## Complexity Tracking

| 변경 | 왜 필요한가 | 더 단순한 대안을 쓰지 않은 이유 |
|-|-|-|
| `RawSignal.Tick` 과 `MockSignalSource` 틱 방출, `EngineCore` 틱 처리 | `DrivingEngine` 은 신호가 와야만 시간이 진행된다. 데모(23초 급커브 뒤 30초까지 신호 없음)에서 찌그러짐 표정이 약 7초 남는다 | `DrivingEngine` 에 벽시계나 `delay` 타이머를 넣으면 `IMMEDIATE` 재생과 테스트의 결정성이 깨지고 헌장 II, III 에 어긋난다. 소스가 자기 시간 기준으로 틱을 내면 어떤 재생 속도에서도 맞다 |
| `DrivingEngine.injectRestriction(state)` | 데모에서 제한 상태를 수동으로 바꿔 정적/풀 모션을 비교해야 한다(US6). 같은 경로로 엔진에 신호를 넣어야 모션 등급 판단이 엔진 한 곳에만 남는다(헌장 I) | UI 층에서 `tier` 를 덮어쓰면 등급 판단 위치가 둘이 되어 헌장 I 위반이고, 나중에 제품 경로로 새어 나갈 위험이 있다 |

## Post-Design Constitution Re-check

Phase 1 설계(data-model, contracts)를 마친 뒤 다시 점검했다. 모두 PASS 이다.
- `PetController.needsFrames` 가 정적 등급에서 `false` 이고 `PetView` 는 그 값으로만 프레임 루프를 시작한다(원칙 I).
- `:pet` 공개 API 는 `:engine` 타입만 노출하고 SDK 타입은 없다(원칙 II).
- 물리 시간이 `step(dt)` 로만 진행해서 모든 물리 규칙을 고정된 `dt` 와 시드 난수로 JVM 에서 테스트한다(원칙 III).
- 미결 항목은 `research.md` 의 "미결 항목"에 모았다(원칙 VI).
