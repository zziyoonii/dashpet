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

## 테스트 실행

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :signal:testDebugUnitTest :engine:testDebugUnitTest --console=plain
```

