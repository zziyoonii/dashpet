# DashPet 아키텍처

Pleos Connect(AAOS) 위에서 도는 Activity 기반 멀티 윈도우 앱. 캐릭터는 Compose Canvas 로 직접 그린다.

## 모듈과 의존 방향

```
:app  ->  :pet  ->  :engine  ->  :signal
```

| 모듈 | 패키지 | 역할 |
|-|-|-|
| `:app` | `com.example.dashpet` | Application. MainActivity, `ui/`(theme, screens). 매니페스트, 주행 규제/Back/로터리 설정 |
| `:pet` | `...dashpet.pet` | Compose 캐릭터(PetView, PetController). `:engine` 의 상태를 그린다 |
| `:engine` | `...dashpet.engine` | 이벤트 엔진, 상태 머신, 주행 상태 게이트. JVM 단위 테스트 대상 |
| `:signal` | `...dashpet.signal` | RawSignal, VehicleSignalSource, MockSignalSource, PleosSignalSource |

의존은 한 방향만 허용한다. 역방향 참조와 순환은 금지.

## 규칙

- Pleos SDK(`ai.pleos.playground:*`) 의존성과 호출은 `:signal` 에만 둔다.
- 주행 상태(주행 규제) 판단은 `:engine` 에서만 한다. `:app`/`:pet` 은 판단 결과를 받아 UI 만 바꾼다.
- `:pet` 은 SDK 와 `:signal` 의 구현체를 직접 보지 않는다. 필요한 값은 `:engine` 을 거친다.
- `:engine` 은 Android 프레임워크 의존을 최소화한다(`:signal` 이 Android library 라 모듈 타입은 Android library).
- `pleos.car.permission.*` 는 승인 범위 확인 후 필요한 것만 `:app` 매니페스트에 추가한다.
- 비동기 결과는 Flow(kotlinx-coroutines)로 전달하고, UI 갱신은 메인 스레드에서 한다.

## `:signal` 구성 (001-driving-event-core)

| 파일 | 내용 |
|-|-|
| `RawSignal.kt` | sealed `RawSignal`(`Speed`, `Heading`, `ParkedState`, `BrakePedal`, `TurnSignal`, `VehicleWarning`, `LaneChange`, `Restriction`)과 `TurnState`, `WarningType`, `Side`, `RestrictionState`. 속도는 0 이상, 방위각은 0 이상 360 미만으로 정규화 |
| `VehicleSignalSource.kt` | `signals: Flow<RawSignal>`, `start()`, `stop()`. 엔진이 신호를 받는 유일한 경로 |
| `MockSignalSource.kt` | 시나리오 재생 소스. `Pacing.REAL_TIME`(데모) / `Pacing.IMMEDIATE`(테스트) |
| `scenario/` | `Scenario.expand()`(램프를 100ms 간격으로 풀기), `scenario { at(ms) { ... } }` DSL, `DemoScenarios.demoCommute` |

## `:engine` 구성 (001-driving-event-core)

시간은 신호의 `timestampMs` 와 `advanceTo` 로만 진행한다(벽시계, 난수 금지). `android.*`, `ai.pleos.*` 를 쓰지 않는다.

| 파일 | 역할 |
|-|-|
| `EngineConfig.kt` | 모든 임계값, 재발 간격, 지속 시간, 점수 가중치(한 곳) |
| `Kinematics.kt`, `AccelEventDetector.kt` | 평활화와 가속도 계산, 급가속·급제동·급커브 판정 |
| `WarningEventDetector.kt`, `LaneChangeEventDetector.kt` | 차선 이탈/충돌 위험, 좋은/깜빡이 없는 차선 변경 |
| `TripTracker.kt`, `ScoreKeeper.kt` | 주행 수명주기와 기록, 점수 |
| `MoodMachine.kt`, `Mood.kt` | 우선순위 기반 감정 전이 |
| `MotionGate.kt`, `PetOutput.kt` | 주행 제한 상태 -> 모션 등급(등급 판단은 여기서만), 출력 타입 |
| `EngineCore.kt` | 위를 묶는 동기 코어(`process`, `advanceTo`, `currentTrip`, `completedTrips`) |
| `DrivingEngine.kt` | 코루틴 래퍼(`output: StateFlow<PetOutput>`, `events: SharedFlow<DrivingEvent>`) |

## `:pet` 구성 (002-pet-view)

`:pet` 은 `:engine` 의 출력(`PetOutput`)과 이벤트(`DrivingEvent`)를 받아 펫을 그린다. 판단은 하지 않는다(모션 등급은 `:engine` 이 정한다).

| 계층 | 파일 | 역할 |
|-|-|-|
| 물리 | `SoftBody.kt`, `PetPhysics.kt`, `PetPhysicsConfig.kt` | 몸 22점 소프트바디, 쏠림/번짐/점프/숨쉬기, 크기 배율 용수철. 상수는 `PetPhysicsConfig` 한 곳(시안 값, 초안은 `[확인 필요]`) |
| 컨트롤러 | `PetController.kt`, `PetGeometry.kt`, `Particles.kt`, `PetPose.kt` | 엔진 출력을 받아 풀/정적 모드를 정하고 물리와 파티클을 진행, 그릴 상태(`PetRenderState`) 제공. 윙크, 배경 감정, 정적 포즈(`petPose`) |
| 페인터 | `PetPainter.kt`(풀), `StaticPainter.kt`(정적), `BodyPainter.kt`, `FacePainter.kt`, `EffectsPainter.kt`, `BackgroundPainter.kt`, `PaintUtil.kt`, `PetPalette.kt` | 상태를 읽기만 하고 그린다. 난수와 시계를 읽지 않는다 |
| 뷰 | `PetView.kt`, `MotionPolicy.kt` | Compose 진입점. 프레임 루프(`withFrameNanos`)는 여기에만 있고 `STARTED` 이면서 풀 모션일 때만 돈다. 이름 라벨(14sp)을 왼쪽 위에 표시 |

핵심 규칙:

- **정적은 그리기만 한다.** 정적(`STATIC`, `ONE_SHOT`, 알 수 없는 등급)에서는 프레임 루프, 물리, 파티클, 터치 처리가 없고 입력이 바뀔 때만 다시 그린다.
- **`Tick` 규칙**: 시계는 `step(dt)` 의 `dt` 누적(`PetPhysicsState.clockSec`)뿐이다. `System.currentTimeMillis` 등 벽시계를 쓰지 않고 난수는 주입된 `Random` 만 쓴다(같은 시드와 같은 호출이면 같은 결과). `dt` 는 1/30 초로 자르고, 루프 첫 프레임은 0 이다.
- **`injectRestriction`**(`:engine`) 은 데모 화면 전용이다. 제품 경로(실차 신호)에서는 쓰지 않는다. 실차에서는 플랫폼의 주행 제한이 모션 등급을 정한다.
- **엔진 감정이 우선**: 배경 감정(`setAmbient`)은 엔진 감정이 편안일 때만 보인다. 정적에서는 비의 우비만 그린다.
- 윙크는 `winkClosed(side)`(380ms 주기, 켜진 순간부터 계산), 모션 줄이기에서도 유지한다.

데모 화면(`:app`)은 `DemoViewModel` 이 `MockSignalSource` 의 시나리오를 재생하고, 수동 제한 토글(`injectRestriction`)과 확인용 패널(`DemoControls`)을 제공한다.

## 테스트 실행

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :signal:testDebugUnitTest :engine:testDebugUnitTest :pet:testDebugUnitTest :app:testDebugUnitTest --console=plain
```

