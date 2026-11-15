---

description: "PetView 구현 작업 목록"
---

# Tasks: PetView (펫 캐릭터 그리기와 데모 재생 화면)

**Input**: `specs/002-pet-view/` 의 `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/pet-view-api.md`, `quickstart.md`, `checklists/visual-parity.md`

**Prerequisites**: plan.md, spec.md (완료). 001 기능(`:signal`, `:engine`)이 완료되어 있어야 한다(완료, 브랜치 `001-driving-event-core` 위에서 시작).

**Tests**: 물리, 컨트롤러, 포즈, 루프 정책, 엔진 확장은 **테스트를 먼저 쓴다**(헌장 III). 그리기(`DrawScope`)는 JVM 에서 검증할 수 없으므로 `@Preview` 와 시안과 나란히 비교하는 체크리스트(`visual-parity.md`), 에뮬레이터 측정(`quickstart.md`)으로 검증한다.

**Organization**: 스토리별로 묶었다. 각 스토리는 독립적으로 테스트할 수 있다.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 다른 파일이고 아직 끝나지 않은 작업에 의존하지 않아 병렬로 할 수 있음
- **[Story]**: 스펙의 사용자 스토리(US1~US7)

## Path Conventions

- `signal/...`, `engine/...`, `pet/...`, `app/...` 는 각 모듈의 `src/main/java/com/example/dashpet/<경로>/` 를 줄여 쓴 것이다. 테스트는 `src/test/java/com/example/dashpet/<경로>/` 이다.
  - `:signal` → `.../signal/`, `:engine` → `.../engine/`, `:pet` → `.../pet/`, `:app` → `.../` (패키지 `com.example.dashpet`, 화면은 `.../ui/`)
- 실행 환경: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"` 후 `./gradlew :signal:testDebugUnitTest :engine:testDebugUnitTest :pet:testDebugUnitTest :app:testDebugUnitTest --console=plain`

**plan.md 와의 차이**: 스토리마다 같은 파일을 고치지 않도록 `PetPainter.kt` 를 `BackgroundPainter`, `BodyPainter`, `FacePainter`, `EffectsPainter`, `StaticPainter` 와 조정용 `PetPainter` 로 나누고, 테스트 `PetControllerTest.kt` 를 스토리별 파일(`PetControllerFullTest`, `PetControllerEngineTest`, `PetControllerStaticTest`, `PetControllerTransitionTest`, `PetControllerWinkTest`, `PetControllerAppearanceTest`, `PetControllerAmbientTest`)로 나눴다. plan.md 구조 목록에는 이미 반영했다(T077 에서 최종 구성과 일치하는지 다시 확인한다).

**주의 (스레드)**: `PetController` 는 스레드 안전하지 않다. 프레임 루프(`withFrameNanos`)와 `collectFrom` 은 둘 다 메인 디스패처에서 돌아야 한다(`contracts/pet-view-api.md`).

---

## Phase 1: Setup (공통 설정)

**Purpose**: 빌드 의존성 정리

- [X] T001 `./gradlew :app:dependencies --configuration debugRuntimeClasspath` 로 `lifecycle-viewmodel-compose`, `lifecycle-runtime-compose` 가 이미 해석되는지와 버전을 확인한다(research R17). 결과에 따라 `gradle/libs.versions.toml` 에 `lifecycle = "2.8.3"`(실제 해석된 버전을 따른다) 버전과 라이브러리 `androidx-compose-foundation`(BOM 관리), `androidx-lifecycle-runtime-compose`, `androidx-lifecycle-viewmodel-compose` 항목을 추가한다.
- [X] T002 [P] `pet/build.gradle.kts` 에 `implementation(libs.androidx.compose.foundation)`, `implementation(libs.androidx.lifecycle.runtime.compose)`, `implementation(libs.androidx.compose.ui.tooling.preview)`, `debugImplementation(libs.androidx.compose.ui.tooling)`, `testImplementation(libs.kotlinx.coroutines.test)` 를 추가한다.
- [X] T003 [P] `app/build.gradle.kts` 에 `implementation(libs.androidx.lifecycle.viewmodel.compose)`, `implementation(libs.androidx.lifecycle.runtime.compose)`, `testImplementation(libs.junit)`, `testImplementation(libs.kotlinx.coroutines.test)` 를 추가한다.
- [X] T004 `./gradlew :pet:compileDebugKotlin :app:compileDebugKotlin` 로 컴파일이 되는지 확인한다(compileSdk 34 와 Compose BOM 2025.01.01 유지).

---

## Phase 2: Foundational (모든 스토리의 선행 조건)

**Purpose**: 엔진/신호의 가산 변경(`Tick`, `injectRestriction`)과 `:pet` 의 공통 값 타입

**⚠️ CRITICAL**: 이 단계가 끝나기 전에는 스토리 작업을 시작하지 않는다.

### 테스트 먼저

- [X] T005 [P] `signal/src/test/.../RawSignalTest.kt` 에 `Tick(timestampMs)` 검증을 추가한다: 시각을 가진다, 다른 신호와 같은 시각 순서 규칙(감소하지 않음).
- [X] T006 [P] `signal/src/test/.../MockSignalSourceTest.kt` 에 틱 테스트를 추가한다: `tickIntervalMs`(기본 100), `tailMs`(기본 3000), 시나리오의 첫 신호부터 마지막 신호 + `tailMs` 까지 100ms 간격으로 `Tick` 을 섞어 방출, 같은 시각이면 "신호가 먼저, `Tick` 이 나중", `IMMEDIATE` 와 `REAL_TIME`(가상 시간)이 같은 열을 냄, `tickIntervalMs = 0` 이면 틱 없음(기존 동작), 같은 시나리오 → 같은 스트림.
- [X] T007 [P] `engine/src/test/.../EngineCoreTickTest.kt`: `Tick` 은 이벤트를 만들지 않고 시간만 진행한다, 틱만으로 감정이 지속 시간(`SQUASHED` 1200ms 등) 뒤 `IDLE` 로 돌아간다, 직전보다 작은 시각의 `Tick` 은 무시(예외 없음), 틱이 점수와 기록에 영향 없음, 데모 시나리오를 틱 포함으로 재생하면 22초 급커브 뒤 감정이 약 24초 안에 `IDLE` 로 돌아가고 이벤트 7개와 점수 94 는 그대로(회귀).
- [X] T008 [P] `engine/src/test/.../DrivingEngineTest.kt` 에 `injectRestriction` 테스트를 추가한다: 시작 전 호출은 시각 0 으로 처리, 재생 중 호출은 마지막 처리 시각으로 처리되어 출력의 모션 등급이 바뀜(`NONE` → `FULL`, `ACTIVE`/`UNKNOWN` → `STATIC`), 호출이 소스의 신호와 같은 처리 경로(순서 보장)에서 처리됨, 수동 값은 다음 시나리오 `Restriction` 신호까지 유지.
- [X] T009 [P] `pet/src/test/.../PetAppearanceTest.kt`: `PetAppearance` 기본값("값이 없을 때": `name = "내 차"`, `bodyColor = #4F7CFF`, `personality = STURDY`), 이름이 비어 있으면 기본 이름, 입력 때문에 예외를 던지지 않는다(FR-021).
- [X] T010 [P] `pet/src/test/.../PetPhysicsConfigTest.kt`: `research.md` R6 의 시안 상수를 값으로 고정한다 — 소프트바디 점 22개, `K = 110`, `C = 5`(모션 줄이기 16), `KN = 240`, 프레임당 3회 분할, "변위 제한 −0.32~0.42", 스케일 스프링 `k = 230`, `c = 9`(모션 줄이기 18), "`sx` 0.55~1.7, `sy` 0.5~1.6", 좌우 힘 `2600 + 3400·s`, 지속 `500 + 800·s` ms, 앞뒤 지속 급제동 `450 + 750·s`·급가속 `400 + 650·s` ms, 중력 2600, 점프 속도 활발 820·그 외 650, "파티클 최대 60개".

### 구현

- [X] T011 `signal/src/main/.../RawSignal.kt` 에 `Tick(timestampMs)` 를 추가한다(T005 를 통과시킴).
- [X] T012 `engine/src/main/.../EngineCore.kt` 에서 `Tick` 을 `advanceTo(timestampMs)` 로 처리한다(이벤트 없음, T007 을 통과시킴, T011 의존).
- [X] T013 `signal/src/main/.../MockSignalSource.kt` 에 `tickIntervalMs = 100`, `tailMs = 3000` 매개변수를 추가하고 틱을 결정적으로 섞어 방출한다(T006 을 통과시킴, T011 의존).
- [X] T014 `engine/src/main/.../DrivingEngine.kt` 에 `injectRestriction(state: RestrictionState)` 를 추가한다: 소스와 주입 신호를 한 코루틴에서 순서대로 처리하고(예: `merge(source.signals, injected)`), 주입 신호의 시각은 마지막으로 처리한 신호의 시각(시작 전 0)으로 한다. 기존 `start()` 새 세션 규칙은 유지한다(T008 을 통과시킴, T011 의존).
- [X] T015 [P] `pet/src/main/.../PetAppearance.kt`: `PetAppearance(name, bodyColor, personality)`, `Personality`(`STURDY`, `LIVELY`, `CUTE`), `AmbientMood`(`HOT`, `COLD`, `RAIN`, `QUIET`)(T009 를 통과시킴).
- [X] T016 [P] `pet/src/main/.../PetPalette.kt`: 시안 팔레트 상수(`bg #0a0e14`, `panel #111821`, `line #233040`, `ink #e6ebf1`, `muted #8796a8`, `accent #ffd27a`, `good #3ddc84`, `warn #f5a524`, `crit #ff5a5a`, 호박색 `#ffb020`).
- [X] T017 [P] `pet/src/main/.../PetPhysicsConfig.kt`: 시안 상수를 한 곳에 둔다(각 값에 `research.md` 항목 번호와 시안 출처 주석). T010 을 통과시킴.
- [X] T018 `pet/src/main/.../ModulePlaceholder.kt` 를 지운다(T015 이후).
- [X] T019 [P] 001 문서에 가산 변경을 반영한다: `specs/001-driving-event-core/contracts/signal-source.md`(신호 소스가 `Tick` 을 주기적으로 방출해야 한다는 규칙, `MockSignalSource` 매개변수), `specs/001-driving-event-core/data-model.md`(`RawSignal` 표에 `Tick`).
- [X] T020 `./gradlew :signal:testDebugUnitTest :engine:testDebugUnitTest :pet:testDebugUnitTest --console=plain` 로 Foundational 과 001 회귀(기존 145개)가 모두 통과하는지 확인한다.

**Checkpoint**: 틱과 수동 제한 주입이 동작하고 `:pet` 의 공통 값 타입이 준비됨. 스토리 작업 시작 가능.

---

## Phase 3: User Story 1 - 풀 모션 감정과 물리 반응 (Priority: P1) 🎯 MVP

**Goal**: 풀 모션에서 엔진 출력과 이벤트가 감정 표현과 방향별 물리 반응으로 그려진다(시안의 parked 표현 이식).

**Independent Test**: 엔진 없이 감정, 강도, 방향을 직접 넣어서(고정 입력) 물리 상태를 JVM 테스트로 확인하고, `@Preview` 로 모양을 시안과 비교한다(US1 시나리오 1~6).

### Tests for User Story 1 ⚠️ (구현 전에 먼저 쓰고 실패를 확인)

- [X] T021 [P] [US1] `pet/src/test/.../SoftBodyTest.kt`: 점 22개, 충격(`kick`) 뒤 출렁임이 감쇠, "변위 제한 −0.32~0.42" 를 넘지 않음, 모션 줄이기(`C = 16`, `kick` 진폭 ×0.4)에서 더 빨리 가라앉음, 같은 입력이면 같은 결과.
- [X] T022 [P] [US1] `pet/src/test/.../PetPhysicsTest.kt`: 고정 `dt` 로 확인 — 급커브(`dir = +1`)는 오른쪽 벽으로 밀려 `ox` 가 양수, 지속 시간 `0.5 + 0.8·s` 초, 세기가 클수록 `pen` 과 힘이 큼, 벽 접촉 시 `contact = max(20, W/2 − PANEL − rxMax)`, 종료 시 `vx = −dir·(170 + 200·s)`; 급제동은 `z` 가 1 에 닿아 `press` 가 오르고 유리 접촉 충격은 한 번만, 종료 시 자국(`smudge`)이 남고 1.8초 뒤 사라짐; 급가속은 `z` 가 −0.75 로 가서 `lean` 이 생김; 점프는 중력 2600 으로 떨어지고 착지 충격이 스케일에 반영("`sx` 0.55~1.7, `sy` 0.5~1.6" 제한); 감정별 목표 스케일(편안은 `sin(2.2t)` 호흡); `s` 와 `dt` 범위("`dt` 는 (0, 1/30] 로 자른다").
- [X] T023 [P] [US1] `pet/src/test/.../ParticlesTest.kt`: 풀 모션 기쁨에서 반짝임 생성, "파티클 최대 60개" 상한, 프레임 시간이 24ms 를 넘는 상태가 30프레임 연속되면 생성 중단하고 회복되면 재개(FR-014), 모션 줄이기에서는 생성하지 않음, 수명이 끝나면 제거, 시드 고정 시 결정적.
- [X] T024 [P] [US1] `pet/src/test/.../PetControllerFullTest.kt`: 풀 모션에서 `HARD_CORNER` 방향 `RIGHT` → `corner(+1, s)`, `LEFT` → `corner(-1, s)`, `HARD_BRAKE`(`FRONT`) → `surge(+1, s)`, `HARD_ACCEL`(`BACK`) → `surge(-1, s)`, 다른 이벤트 종류는 물리 반응 없음, 감정이 `HAPPY` 로 진입할 때 점프와 `STARTLED` 진입 충격, 같은 감정이 이어지는 이벤트도 쏠림은 매번 일어남, 같은 시드와 같은 호출 열이면 `renderState` 가 같음(SC-009), `step` 이 시계를 `dt` 만큼 진행.
- [X] T025 [P] [US1] `pet/src/test/.../PetControllerEngineTest.kt`(coroutines-test `runTest`): `PetController.collectFrom(engine)` 가 `output` 과 `events` 를 함께 수집, `DemoScenarios.demoCommute` 를 `MockSignalSource(IMMEDIATE)` 로 재생하면 22초 급커브와 46초 급제동에서 쏠림이 일어나고 감정 순서가 엔진과 같음, 취소하면 함께 멈춤.

### Implementation for User Story 1

- [X] T026 [P] [US1] `pet/src/main/.../SoftBody.kt`: 22점 소프트바디, `kick(m, amp, phase)`, `step(dt)`(semi-implicit Euler, 3회 분할)(T021 을 통과시킴).
- [X] T027 [P] [US1] `pet/src/main/.../Particles.kt`: 파티클 풀(상한 60), 종류(`SPARK`, `SWEAT`, `RAIN`, `Z`, `PUFF`, `GLASS_DROP`), 성능 저하 감지와 생성 중단, 시드 가능한 난수(T023 을 통과시킴).
- [X] T028 [US1] `pet/src/main/.../PetPhysics.kt`: 좌우(`Lateral`), 앞뒤(`Longitudinal`, 유리/등받이 접촉), 점프, 스케일 스프링, `lean`, `press`, `smudge`, 소프트바디 연결, 시안의 `physics()` 이식(T022 를 통과시킴, T026 과 T017 의존).
- [X] T029 [US1] `pet/src/main/.../PetController.kt`: 풀 모션 경로 — `applyOutput`(감정, 강도, 방향, 방향지시등, 모션 등급 저장), `onEvent`(위 매핑 표), 감정 진입 효과(점프, 깜짝 충격), `step(dt)`, `setViewport`, `setReducedMotion`, `needsFrames`, `renderState`, `suspend fun PetController.collectFrom(engine: DrivingEngine)`. 난수와 시계는 주입 가능하게 한다(T024, T025 를 통과시킴, T028 과 T027 의존).
- [X] T030 [P] [US1] `pet/src/main/.../BackgroundPainter.kt`: 그라데이션 배경, 격자선, 바닥 빛, 바닥선, 양쪽 패널(벽), 벽 접촉 빛(`contactGlow`)(시안 `drawBackground`, `contactGlow` 이식).
- [X] T031 [P] [US1] `pet/src/main/.../BodyPainter.kt`: 몸 경로(22점 중점 이차 곡선), 그라데이션과 광택, 윤곽선, 그림자, 유리에 눌린 번짐(`e` 보간), 급가속의 눌린 자국과 속도선(시안 `bodyPath`, `drawDent`, `drawSpeedLines`, `drawPet` 의 몸 부분 이식).
- [X] T032 [P] [US1] `pet/src/main/.../FacePainter.kt`: 헤드라이트 눈(하우징, 감정별 빛, 동공 이동, 눈꺼풀), LED 눈썹(감정별 각도), 입(감정별), 볼터치 기본 규칙(`research.md` R7 글로우 근사: 넓은 반투명 선 + 좁은 밝은 선)(시안 `headlight`, `mouth` 이식, 풀 모션의 편안/기쁨/찌그러짐/어지러움/깜짝).
- [X] T033 [P] [US1] `pet/src/main/.../EffectsPainter.kt`: 파티클 그리기, 유리 반사광과 입김 자국(`drawGlass`)(시안 `drawParticles`, `drawGlass` 이식).
- [X] T034 [US1] `pet/src/main/.../PetPainter.kt`: 위 페인터를 순서대로 호출(배경 → 몸 → 얼굴 → 파티클 → 유리)하는 조정 함수. 풀 모션 경로만(T030~T033 의존).
- [X] T035 [US1] `pet/src/main/.../PetView.kt`: `PetView(controller, appearance, modifier)` — 영역 크기를 컨트롤러에 전달, `Canvas` 에서 `PetPainter` 호출, `controller.needsFrames` 일 때만 `withFrameNanos` 루프로 `step(dt)`(`dt` 자르기 1/30) 호출. 영역이 0 이하이면 그리지 않음(T029, T034 의존).
- [X] T036 [P] [US1] `pet/src/main/.../PetPreviews.kt`: `@Preview` — 풀 모션 감정 5종, 급제동 번짐 중간 상태, 급커브 좌/우 눌림 상태, 급가속 꺼짐 상태(고정된 `renderState` 로 그림). 시안 비교의 1차 확인용.

**Checkpoint**: US1 이 단독으로 동작한다. 프리뷰로 감정 5종과 방향 4종의 모양을 시안과 비교할 수 있다. (11/15 영상의 풀 모션 장면 토대)

---

## Phase 4: User Story 2 - 제한 중에는 정적 표현만 (Priority: P1)

**Goal**: 모션 등급이 정적이면(일회성, 알 수 없음 포함) 움직임 없는 정적 포즈와 색으로만 표현하고, 프레임 루프도 터치 처리도 없다.

**Independent Test**: 모션 등급을 정적으로 고정하고 모든 감정과 방향을 순서대로 넣어서, `PetPose` 값만 바뀌고 물리와 파티클이 전혀 없는지 확인한다(US2 시나리오 1~6).

### Tests for User Story 2 ⚠️

- [X] T037 [P] [US2] `pet/src/test/.../PetPoseTest.kt`: 편안은 몸 색 그대로, 기쁨은 `#3DDC84` 와 0.9 혼합, 어지러움은 `#F5A524` 0.8, 깜짝은 `#FF5A5A` 0.85(깜빡임 없음 — 시간 입력이 없는 순수 함수), 찌그러짐은 방향별 기울기(좌/우 ±10°, 앞 1.08 배, 뒤 0.9 배, 초안), 방향지시등 쪽 눈을 선으로 감고 `#FFB020` 눈썹을 정적으로 표시, 같은 입력이면 항상 같은 값.
- [X] T038 [P] [US2] `pet/src/test/.../PetControllerStaticTest.kt`: 초기 모드는 정적(`needsFrames == false`), 정적에서 `onEvent`, 감정 진입 효과, `step` 이 어떤 상태도 바꾸지 않음("반복 모션, 물리 시뮬레이션, 파티클, 유리 번짐, 전환 효과를 쓰면 안 되고"), `ONE_SHOT` 과 알 수 없는 등급은 정적과 같음(FR-008), 감정이 바뀌면 `frame` 번호 증가 없이 포즈만 바뀜, 정적에서 `renderState.physics` 없음.

### Implementation for User Story 2

- [X] T039 [US2] `pet/src/main/.../PetPose.kt`: `(Mood, Direction4?, TurnState, PetAppearance, AmbientMood?) -> StaticPose` 순수 함수. `StaticPose` 는 `bodyColor`, `tiltDeg`, `scaleX`, `scaleY`, `closedEye`, `amberBrow`, `hood`(T037 을 통과시킴, `[확인 필요]` 초안 값 주석).
- [X] T040 [US2] `pet/src/main/.../PetController.kt` 에 정적 경로를 추가한다: 등급이 `STATIC`/`ONE_SHOT`/알 수 없음이면 정적 모드, 이벤트와 진입 효과 무시, `renderState` 에 `StaticPose` 반영(T038 을 통과시킴, T039 의존).
- [X] T041 [P] [US2] `pet/src/main/.../StaticPainter.kt`: 정적 포즈 그리기 — 단색 몸, 윤곽선 `rgba(255,255,255,.18)`, 어두운 하우징 눈(동공 없음), 입 없음, 정적 윙크(감은 눈 선 + 호박색 눈썹 선), 우비 실루엣(시안의 moving 분기에서 움직임 제거)(T039 의존).
- [X] T042 [US2] `pet/src/main/.../PetView.kt` 에 정적 분기를 추가한다: 프레임 루프 없음, `pointerInput` 없음, 입력이 없으면 다시 그리지 않음. `PetPreviews.kt` 에 정적 `@Preview`(감정 5종, 찌그러짐 4방향, 윙크)를 추가한다(T040, T041 의존).

**Checkpoint**: US1 + US2 가 함께 동작한다. 정적 등급에서 프레임 루프가 없다.

---

## Phase 5: User Story 3 - 제한 상태 변화에 즉시 반응 (Priority: P1)

**Goal**: 풀 모션 중 제한이 걸리면 같은 순간 모든 움직임이 멈추고, 풀려도 지난 이벤트를 재현하지 않으며, 백그라운드에서는 루프와 시간 진행이 멈춘다.

**Independent Test**: 풀 모션 반응 도중에 등급을 정적으로 바꾸고 그 호출 안에서 물리와 파티클이 초기화되는지, 루프 정책이 올바른지 확인한다(US3 시나리오 1~3).

### Tests for User Story 3 ⚠️

- [X] T043 [P] [US3] `pet/src/test/.../MotionPolicyTest.kt`: `shouldRunLoop(mode, lifecycleStarted)` 는 풀 모션이고 `STARTED` 일 때만 참, 정적이나 백그라운드는 거짓, 재개 시 첫 `dt` 는 0, `dt` 는 (0, 1/30] 로 자름, 모션 줄이기 보정값(`C = 16`, `c = 18`, `kick × 0.4`, 파티클/속도선/흔들림 생략)을 돌려줌.
- [X] T044 [P] [US3] `pet/src/test/.../PetControllerTransitionTest.kt`: 급제동 번짐이 진행 중인 풀 모션에서 등급을 정적으로 바꾸면 같은 호출 안에서 `physics` 와 파티클이 초기화되고 `needsFrames` 가 거짓("서서히 줄어드는 효과 없음"), 정적 → 풀 전환 시 물리를 새로 시작하고 정적이던 동안의 이벤트를 재현하지 않음, 풀 → 정적 → 풀 반복에도 상태가 새는 것이 없음, 알 수 없는 등급 값도 정적(예외 없음).

### Implementation for User Story 3

- [X] T045 [US3] `pet/src/main/.../MotionPolicy.kt`: `shouldRunLoop`, 첫 프레임 `dt = 0` 규칙, `dt` 자르기, 모션 줄이기 보정(T043 을 통과시킴).
- [X] T046 [US3] `pet/src/main/.../PetController.kt` 에 전환 처리를 추가한다: 풀 → 정적 즉시 초기화(물리, 파티클, `smudge`, 접촉 플래그), 정적 → 풀 새로 시작(T044 를 통과시킴, T045 의존).
- [X] T047 [US3] `pet/src/main/.../PetView.kt` 를 갱신한다: 프레임 루프를 `repeatOnLifecycle(Lifecycle.State.STARTED)` 안에서만 돌리고 `MotionPolicy` 로 시작/중지, 재개 시 첫 `dt = 0`, `STARTED` 로 돌아올 때 `ValueAnimator.areAnimatorsEnabled()` 로 모션 줄이기를 다시 읽어 `setReducedMotion` 호출(T045, T046 의존).

**Checkpoint**: P1 스토리 3개(US1~US3)가 모두 동작한다. 11/15 영상에 필요한 반응 코어가 갖춰진다.

---

## Phase 6: User Story 6 - 앱 홈 데모 재생 (Priority: P2)

**Goal**: 앱 홈에서 데모 출근 시나리오를 재생, 정지, 다시 재생하고, 제한 상태를 수동으로 바꾸고, 확인용 표시를 볼 수 있다(11/15 영상 촬영 경로).

**Independent Test**: 홈에서 재생해 52초 시나리오를 끝까지 보고 이벤트 시점마다 펫이 반응하는지, 제한 상태 토글이 즉시 반영되는지 확인한다(US6 시나리오 1~5).

> 스토리 번호는 스펙을 따르고, 영상 우선순위에 맞춰 US4, US5 보다 먼저 진행한다(P2 중 영상에 필요한 것).

### Tests for User Story 6 ⚠️

- [X] T048 [P] [US6] `app/src/test/.../ui/demo/DemoViewModelTest.kt`(`Dispatchers.setMain`, coroutines-test): `togglePlay` 가 정지 중이면 처음부터 재생·재생 중이면 정지, 끝까지 가면 정지 상태로 돌아옴, `stop` 이 편안 상태로(엔진 새 세션), `setRestriction` 이 `injectRestriction` 으로 엔진 출력의 모션 등급을 바꿈, 재생 중이 아니어도 동작, `debugAvailable == false` 이면 `setRestriction` 무시, 경과 시간 표시, `DemoViewModel(scenario, pacing, debugAvailable)` 생성자 주입.

### Implementation for User Story 6

- [X] T049 [US6] `app/src/main/.../ui/demo/DemoViewModel.kt`: `ViewModel` 이 `MockSignalSource(DemoScenarios.demoCommute, REAL_TIME)` 와 `DrivingEngine` 을 소유, `output`, `events`, `demo`(`DemoState`) 노출, `togglePlay`, `stop`, `setRestriction`, `setDebugPanelVisible`. 확인용 시각은 ViewModel 경과 타이머(표시만). `viewModelScope`(메인)에서 동작(T048 을 통과시킴, T014 의존).
- [X] T050 [P] [US6] `app/src/main/.../ui/demo/DemoControls.kt`: 재생/정지 버튼, 제한 상태 토글(없음/있음/알 수 없음), 확인용 패널(현재 감정, 모션 등급, 시나리오 시각, 숨기기). 텍스트는 12sp 이상, 대비 4.5:1 이상. 디버그 불가 빌드에서는 표시하지 않음.
- [X] T051 [US6] `app/src/main/.../ui/screens/HomeScreen.kt` 를 교체한다: `PetView` + `LaunchedEffect` 로 `controller.collectFrom(engine)`, `DemoControls` 배치, 앱의 `ApplicationInfo.FLAG_DEBUGGABLE` 로 `debugAvailable` 계산, 데모 외형(`"장수"`, `#4F7CFF`, `STURDY`) 사용, 세로/가로 모두 반응형(T049, T050, T035 의존). `@Preview` 포함.
- [X] T052 [US6] 수동 확인: 에뮬레이터 또는 프리뷰에서 재생이 끝까지 가고 22/38/46초 장면에서 펫이 반응하는지, 8~52초에 풀 모션이 없는지 `quickstart.md` B, C, H 로 확인하고 결과를 기록한다.

**Checkpoint**: 11/15 영상용 데모 재생이 가능하다(US1~US3 + US6).

---

## Phase 7: User Story 4 - 방향지시등 윙크 (Priority: P2)

**Goal**: 풀 모션에서 방향지시등 쪽 눈이 380ms 마다 윙크한다(정적 윙크는 US2 에서 완료).

**Independent Test**: 방향지시등 값만 바꿔 넣고 윙크 상태가 380ms 마다 바뀌는지 확인한다(US4 시나리오 1, 3).

### Tests for User Story 4 ⚠️

- [X] T053 [P] [US4] `pet/src/test/.../PetControllerWinkTest.kt`: 풀 모션에서 `turnSignal == RIGHT` 이면 화면 기준 오른쪽 눈이 `floor((clock − start) / 0.38) % 2 == 0` 일 때 감김(380ms 주기), `LEFT` 는 왼쪽 눈, `OFF` 가 되면 윙크가 즉시 사라짐, 다른 쪽 눈은 현재 감정 그대로, 모션 줄이기에서도 윙크는 유지, 시계는 `step` 으로만 진행(결정적).

### Implementation for User Story 4

- [X] T054 [US4] `pet/src/main/.../PetController.kt` 에 윙크 상태(`winkClosed(side)`)를 추가한다(T053 을 통과시킴).
- [X] T055 [US4] `pet/src/main/.../FacePainter.kt` 에 풀 모션 윙크를 그린다: 감은 눈은 호박색이 섞인 ^ 모양 빛(`#FFE2A0`, 글로우 `#FFB020`), 그 눈의 눈썹은 호박색(`#FFB020`)(T054 의존). `PetPreviews.kt` 에 윙크 프리뷰를 추가한다.

**Checkpoint**: 풀 모션과 정적 모두 윙크가 동작한다.

---

## Phase 8: User Story 5 - 내 차 외형 반영 (Priority: P2)

**Goal**: 몸 색, 성격(애교 볼터치, 활발 점프), 이름 표시를 반영한다.

**Independent Test**: 서로 다른 외형 3벌로 같은 감정을 그려 차이를 확인한다(US5 시나리오 1~3).

### Tests for User Story 5 ⚠️

- [X] T056 [P] [US5] `pet/src/test/.../PetPaletteTest.kt`: 색 혼합(`mix`), 휘도(`lum = (0.299R + 0.587G + 0.114B) / 255`), 윤곽선 알파 규칙(`lum < 0.3` 이면 0.28, 아니면 0.2), 대비율 계산(`contrastRatio`)과 이름 라벨 색이 배경 대비 4.5:1 이상.
- [X] T057 [P] [US5] `pet/src/test/.../PetControllerAppearanceTest.kt`: `LIVELY` 의 기쁨 점프 초기 속도 820 이 `STURDY` 의 650 보다 높음, 두 번째 점프 620/480, `CUTE` 는 볼터치 플래그가 항상 켜짐, 외형이 없으면 기본 외형, 외형 변경이 물리 상태를 건드리지 않음.

### Implementation for User Story 5

- [X] T058 [US5] `pet/src/main/.../PetPalette.kt` 에 `mix`, `lum`, `contrastRatio` 를 추가한다(T056 을 통과시킴).
- [X] T059 [US5] `pet/src/main/.../PetController.kt` 에 성격 효과를 추가한다: `LIVELY` 점프 속도, `CUTE` 볼터치 플래그(T057 을 통과시킴, T058 의존).
- [X] T060 [US5] `pet/src/main/.../BodyPainter.kt`, `FacePainter.kt`, `StaticPainter.kt` 에서 몸 그라데이션(밝은 혼합 0.32 → 몸 색 → 어두운 혼합 0.3), 광택, 윤곽선 알파, 볼터치(애교 항상, 기쁨 0.4/0.3 알파)를 외형 값으로 계산하도록 연결한다(T058, T059 의존).
- [X] T061 [US5] `pet/src/main/.../PetView.kt` 에 이름 `Text`(14sp, 대비 4.5:1 이상)를 표시한다. 이름이 비어 있으면 기본 이름을 쓴다(T058 의존).

**Checkpoint**: 외형이 반영된다.

---

## Phase 9: User Story 7 - 배경 감정 (Priority: P3)

**Goal**: 더위, 추위, 비, 조용을 풀 모션에서 그리고 정적에서는 비만 우비 실루엣으로 표현한다. 입력은 `setAmbient` 로만 받는다.

**Independent Test**: 배경 감정 4종을 직접 넣어 풀 모션과 정적에서 각각 확인한다(US7 시나리오 1~3).

### Tests for User Story 7 ⚠️

- [X] T062 [P] [US7] `pet/src/test/.../PetControllerAmbientTest.kt`: `setAmbient` 가 풀 모션에서 감정 목표 스케일을 바꿈(더위 1.24/0.74, 추위 0.94/0.95, 깜짝 0.86/0.90, 조용 1.06/(0.9 + 0.02·호흡)), 엔진 감정이 `IDLE` 일 때만 배경 감정이 보이고 다른 감정이 우선, 눈 깜빡임은 편안과 비에서만, 정적에서는 영향 없음(비 제외), `setAmbient(null)` 로 해제.
- [X] T063 [P] [US7] `pet/src/test/.../ParticlesAmbientTest.kt`: 풀 모션에서 더위는 땀, 비는 빗줄기(주차 상태에서 초당 약 55개), 조용은 Z, 추위는 입김을 생성, 정적과 모션 줄이기에서는 생성하지 않음, 상한 60.
- [X] T064 [P] [US7] `pet/src/test/.../PetPoseAmbientTest.kt`: 정적에서 비는 `hood == true`, 더위/추위/조용은 별도 표현 없음(기본 포즈와 같음), 엔진 감정이 우선.

### Implementation for User Story 7

- [X] T065 [US7] `pet/src/main/.../PetController.kt` 에 `setAmbient` 와 배경 감정 처리(목표 스케일, 깜빡임 규칙)를 추가한다(T062 를 통과시킴).
- [X] T066 [P] [US7] `pet/src/main/.../Particles.kt` 에 배경 감정 생성기를 추가한다(T063 을 통과시킴).
- [X] T067 [P] [US7] `pet/src/main/.../PetPose.kt` 에 배경 감정(비의 우비)을 추가한다(T064 를 통과시킴).
- [X] T068 [US7] `BodyPainter.kt`, `FacePainter.kt`, `BackgroundPainter.kt` 에 배경 감정 표현을 그린다: 더위(낮고 넓게 녹음, 땀, 바닥 방울, 반쯤 감긴 눈, 처진 눈썹, 헐떡임), 추위(덜덜 떪, 서리, 입김, 지그재그 입), 비(노란 우비, 빗줄기, 배경 색조), 조용(눈 감은 선, 눈썹 꺼짐, Zzz)(T065 의존). `PetPreviews.kt` 에 배경 감정 프리뷰를 추가한다.

**Checkpoint**: 모든 스토리가 동작한다.

---

## Phase 10: Polish & Cross-Cutting Concerns

- [X] T069 [P] `ARCHITECTURE.md` 를 갱신한다: `:pet` 구성(물리/컨트롤러/페인터/뷰), `Tick` 규칙, `injectRestriction`(제품 경로 사용 금지), 데모 화면.
- [X] T070 [P] `specs/002-pet-view/checklists/traceability.md` 에 FR-001~FR-021 과 SC-001~SC-009 를 테스트 이름, `visual-parity.md` 항목, `quickstart.md` A~J 로 연결한 표를 만든다(빠진 항목이 있으면 테스트를 추가).
- [X] T071 헌장 점검(grep): `pet/src/main` 에서 `rememberInfiniteTransition`, `animate*AsState`, `Animatable`, `InfiniteTransition`, `Handler`, `Timer` 를 쓰지 않았는지, `withFrameNanos` 가 `PetView` 의 루프 한 곳에만 있는지, `System.currentTimeMillis`, `System.nanoTime`, `Date` 를 쓰지 않았는지(물리 시계는 `dt` 누적), `ai.pleos` 와 `:signal` 구현체 import 가 없는지 확인한다. `engine/src/main`, `signal/src/main` 의 001 점검(벽시계, `android.`, `ai.pleos`)도 다시 확인한다.
- [X] T072 `./gradlew assembleDebug :signal:testDebugUnitTest :engine:testDebugUnitTest :pet:testDebugUnitTest :app:testDebugUnitTest --console=plain` 을 실행하고 JUnit XML 로 모듈별 테스트 개수와 실패 0, 001 회귀(기존 145개) 통과를 확인한다.
- [ ] T073 `quickstart.md` 2번 A~J 를 에뮬레이터에서 확인하고 결과(특히 D 정적 프레임 수, E fps)를 `specs/002-pet-view/checklists/verification-log.md` 에 기록한다. 에뮬레이터가 없으면 확인하지 못한 항목과 이유를 명시한다.
- [ ] T074 `checklists/visual-parity.md` 를 시안과 나란히 놓고 채우고, 정적 포즈 초안 값(기울기, 배율, 색 혼합)을 기획자와 확정한다. 시안과 다르게 두기로 한 항목과 이유를 기록하고 기획자 승인을 받는다(SC-001).
- [ ] T075 데모 영상용으로 `quickstart.md` 2번 B, H 를 3회 반복해 매번 같은 이벤트 순서와 반응이 나오는지 확인한다(촬영 반복 가능, SC-009).
- [X] T076 `specs/002-pet-view/research.md` 의 미결 항목 4건(정적 포즈 값, 에뮬레이터 성능, 데모 급커브 모양, 글로우 근사)을 결과에 맞춰 갱신한다.
- [X] T077 `specs/002-pet-view/plan.md` 의 구조 목록이 최종 파일 구성과 일치하는지 확인하고, 다르면 갱신한다.
- [X] T078 변경을 스토리 단위로 커밋한다(브랜치 `002-pet-view`). 푸시는 요청이 있을 때만 한다.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 바로 시작. T002, T003 은 T001 이후 서로 병렬.
- **Foundational (Phase 2)**: Setup 이후. 모든 스토리를 막는다. 안에서 테스트(T005~T010)가 구현(T011~T017)보다 먼저다.
- **User Stories (Phase 3~9)**: Foundational 이후. `PetController.kt`, `PetView.kt`, 페인터 파일을 여러 스토리가 고치므로 **우선순위 순서로 진행**한다: US1 → US2 → US3 → US6 → US4 → US5 → US7.
- **Polish (Phase 10)**: 모든 스토리 이후.

### User Story Dependencies

- **US1 (P1)**: Foundational 이후 독립.
- **US2 (P1)**: US1 의 `PetController`, `PetView` 위에 정적 경로를 추가. 테스트와 `PetPose` 는 독립.
- **US3 (P1)**: US1 + US2 위에서 전환과 생명주기 처리.
- **US6 (P2)**: US1 + US3 가 있어야 의미가 있고 Foundational 의 `Tick`, `injectRestriction` 이 필수. 11/15 영상에 필요해서 US4, US5 보다 먼저 진행한다.
- **US4 (P2)**: US1 의 `FacePainter`, US2 의 `StaticPainter` 위에서 풀 모션 윙크를 추가.
- **US5 (P2)**: US1 페인터 위에서 외형 값을 연결.
- **US7 (P3)**: US1, US2 위에서 배경 감정을 추가.

### Within Each User Story

- 테스트 → 실패(또는 컴파일 실패) 확인 → 구현 → 통과 확인 → 다음 스토리.
- 순수 로직(물리, 포즈, 정책) → 컨트롤러 → 페인터 → `PetView` → 화면.

### Parallel Opportunities

- Setup: T002 ∥ T003
- Foundational 테스트: T005 ∥ T006 ∥ T007 ∥ T008 ∥ T009 ∥ T010, 구현: T015 ∥ T016 ∥ T017 ∥ T019
- US1 테스트: T021 ∥ T022 ∥ T023 ∥ T024 ∥ T025, 구현: T026 ∥ T027, 페인터: T030 ∥ T031 ∥ T032 ∥ T033 ∥ T036
- US2: T037 ∥ T038, T041 은 T039 이후 병렬 가능
- US3: T043 ∥ T044
- US6: T048 ∥ T050
- US5: T056 ∥ T057, US7: T062 ∥ T063 ∥ T064, 구현 T066 ∥ T067

## Parallel Example: User Story 1

```text
# US1 테스트를 함께 쓴다:
Task: "SoftBodyTest.kt (pet/src/test/.../SoftBodyTest.kt)"
Task: "PetPhysicsTest.kt (pet/src/test/.../PetPhysicsTest.kt)"
Task: "ParticlesTest.kt (pet/src/test/.../ParticlesTest.kt)"
Task: "PetControllerFullTest.kt (pet/src/test/.../PetControllerFullTest.kt)"

# 페인터를 함께 만든다:
Task: "BackgroundPainter.kt, BodyPainter.kt, FacePainter.kt, EffectsPainter.kt (pet/src/main/.../)"
```

## Implementation Strategy

### 11/15 최소 경로 (US1 → US2 → US3 → US6)

1. Phase 1~2 (Setup, Foundational) — `Tick` 과 `injectRestriction` 이 데모의 전제다.
2. Phase 3 US1 → **멈추고 검증**: 프리뷰로 풀 모션 감정과 방향 4종을 시안과 비교.
3. Phase 4 US2 → 정적 포즈(안전 원칙, 영상의 주행 장면).
4. Phase 5 US3 → 전환과 생명주기(제한이 걸리면 즉시 멈춤).
5. Phase 6 US6 → 데모 재생으로 영상을 찍는다.
6. 이후 US4(윙크), US5(외형), US7(배경 감정) 순으로 마무리한다.

### Incremental Delivery

각 스토리가 끝날 때마다 테스트를 통과시키고 커밋한다. 시각 확인이 필요한 스토리(US1, US2, US4, US5, US7)는 `@Preview` 로 1차 확인하고 에뮬레이터에서 최종 확인한다.

## Notes

- 물리 상수는 `PetPhysicsConfig` 한 곳에서만 정의하고 시안 출처를 주석으로 남긴다(헌장 VI). 값은 테스트로 고정한다.
- 정적 포즈 값(기울기, 배율, 색 혼합)과 파티클 감축 임계값(24ms, 30프레임)은 `[확인 필요]` 초안이다. 코드에 주석으로 표시한다.
- `injectRestriction` 은 데모 컨트롤(디버그 가능 빌드)에서만 호출한다. 제품 화면에서 호출하지 않는다(헌장 I).
- 정적 등급에서는 프레임 루프, 애니메이터, 타이머, 터치 처리가 없어야 한다(FR-006, FR-007).
- 이 기능은 SDK 의존성과 `pleos.car.permission.*` 권한을 추가하지 않는다.
