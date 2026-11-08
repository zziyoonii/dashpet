# Contract: PetView 공개 API (`:pet`, `:app`)

`:app` 이 펫을 화면에 올리는 방법과, 엔진 출력이 펫 반응으로 바뀌는 규칙이다. `:pet` 은 `:engine` 의 타입만 받고 SDK 타입을 노출하지 않는다.

## PetView

```kotlin
@Composable
fun PetView(
    controller: PetController,
    appearance: PetAppearance,
    modifier: Modifier = Modifier,
)
```

규칙:
1. 크기는 `modifier` 가 정한 영역 전체다. 영역이 0 이하이면 아무것도 그리지 않는다.
2. 모션 등급이 풀 모션이고 생명주기가 `STARTED` 일 때만 프레임 루프를 돌린다(`controller.needsFrames`). 그 외에는 루프, 애니메이터, 타이머가 없다.
3. 정적 등급에서는 터치 입력을 처리하지 않는다.
4. 이름은 Compose `Text`(14sp, 대비 4.5:1 이상)로 표시한다. 이름이 비어 있으면 기본 이름을 쓴다.
5. 앱이 `STARTED` 로 돌아오면 모션 줄이기 설정을 다시 읽는다.

## PetController

```kotlin
class PetController(
    config: PetPhysicsConfig = PetPhysicsConfig(),
    random: kotlin.random.Random = kotlin.random.Random.Default,
) {
    /** 상태: 감정, 모션 등급, 방향지시등, 방향(정적 포즈), 강도. 등급이 바뀌면 모드를 전환한다. */
    fun applyOutput(output: PetOutput)

    /** 물리 반응: HARD_CORNER / HARD_BRAKE / HARD_ACCEL. 정적 모드에서는 무시한다. */
    fun onEvent(event: DrivingEvent)

    fun setAmbient(ambient: AmbientMood?)
    fun setReducedMotion(reduced: Boolean)
    fun setViewport(widthPx: Float, heightPx: Float)

    /** 물리 시계를 dt 초 진행한다. 풀 모션에서만 동작하고 정적에서는 아무것도 하지 않는다. dt 는 (0, 1/30] 로 자른다. */
    fun step(dtSec: Float)

    /** 풀 모션이면 true. 프레임 루프를 돌릴지의 유일한 기준. */
    val needsFrames: Boolean

    val renderState: PetRenderState
}

/** 엔진을 컨트롤러에 연결한다. output 과 events 를 함께 수집하고, 취소되면 함께 멈춘다. */
suspend fun PetController.collectFrom(engine: DrivingEngine)
```

### 규칙

1. **결정적:** 같은 설정, 같은 `random` 시드, 같은 `applyOutput`/`onEvent`/`step` 호출 열이면 `renderState` 가 항상 같다. 벽시계에 의존하지 않는다.
2. **정적 전환:** 등급이 정적이 되면 즉시(같은 호출 안에서) 물리 상태를 초기화하고 파티클을 비우고 `needsFrames` 를 `false` 로 만든다. 서서히 줄어드는 효과가 없다.
3. **풀 전환:** 등급이 풀 모션이 되면 물리를 새로 시작한다. 정적이던 동안의 이벤트를 나중에 재현하지 않는다.
4. **예외 없음:** 알 수 없는 값(등급, 방향)은 정적 취급하고 예외를 던지지 않는다.
5. **첫 프레임:** `PetView` 가 루프를 (재)시작할 때 첫 `step` 의 `dt` 는 0 이다.

### 엔진 출력과 이벤트의 매핑

| 입력 | 조건 | 컨트롤러 동작 (풀 모션) | 정적 모션 |
|-|-|-|-|
| `PetOutput.mood` 진입 `HAPPY` | 이전과 다른 감정 | 점프(`LIVELY` 820, 그 외 650 px/s), 760ms 뒤 아직 `HAPPY` 이고 바닥이면 재점프(620/480) | 무시 |
| `PetOutput.mood` 진입 `STARTLED` | 이전과 다른 감정 | 위로 420 px/s, `vsx -= 1.5`, `vsy -= 1` | 무시 |
| `PetOutput.mood` `IDLE`/`DIZZY`/`SQUASHED` | - | 표정 전환, 어지러움은 좌우 비틀거림 | 정적 포즈와 색 |
| `DrivingEvent HARD_CORNER` | 방향 `RIGHT` | `corner(+1, s)` (오른쪽 패널로 쏠림) | 무시 |
| `DrivingEvent HARD_CORNER` | 방향 `LEFT` | `corner(-1, s)` | 무시 |
| `DrivingEvent HARD_BRAKE` | 방향 `FRONT` | `surge(+1, s)` (유리 쪽으로) | 무시 |
| `DrivingEvent HARD_ACCEL` | 방향 `BACK` | `surge(-1, s)` (등 뒤로) | 무시 |
| `PetOutput.turnSignal` `LEFT`/`RIGHT` | - | 같은 쪽 눈이 380ms 마다 윙크, 호박색 눈썹 | 같은 쪽 눈을 선으로 감고 호박색 선(점멸 없음) |
| `PetOutput.tier` | `FULL` / 그 외 | 풀 모드 / 정적 모드 | - |

`s` 는 이벤트의 `intensity`(0~1)다. 다른 이벤트 종류(경고, 점수 이벤트)는 물리 반응을 일으키지 않는다(감정은 `PetOutput` 으로 표현).

## 외형과 배경 감정

`PetAppearance`, `Personality`, `AmbientMood` 는 [data-model.md](../data-model.md) 를 따른다. 배경 감정은 `setAmbient` 로만 넣고, 엔진 출력의 감정과 별개다(엔진 출력에 배경 감정 값을 싣는 일은 이후 기능).

## 가산 변경: 신호와 엔진

### `RawSignal.Tick` (`:signal`)

```kotlin
data class Tick(override val timestampMs: Long) : RawSignal
```

- 엔진은 `Tick` 으로 시간만 진행한다(이벤트 없음).
- **모든 신호 소스는 신호가 없는 구간에도 자기 시간 기준으로 `Tick` 을 주기적으로(권장 100~200ms) 방출해야 한다.** 그렇지 않으면 감정 지속 시간 만료가 다음 신호까지 늦어진다.
- `MockSignalSource(scenario, pacing, tickIntervalMs = 100, tailMs = 3000)`: 시나리오 시각 기준으로 틱을 섞어 낸다(`IMMEDIATE`, `REAL_TIME` 이 같은 열). `tickIntervalMs = 0` 이면 틱 없음.

### `DrivingEngine.injectRestriction` (`:engine`)

```kotlin
fun injectRestriction(state: RestrictionState)
```

- 엔진이 마지막으로 처리한 시각(시작 전이면 0)으로 `Restriction` 신호를 만들어 같은 처리 경로에서 처리한다. 모션 등급 판단은 계속 `MotionGate` 한 곳에서 한다.
- 제품 경로에서 호출하지 않는다. 데모 컨트롤(디버그 가능 빌드)만 호출한다.

## DemoViewModel (`:app`)

```kotlin
class DemoViewModel : ViewModel() {
    val output: StateFlow<PetOutput>
    val events: SharedFlow<DrivingEvent>
    val demo: StateFlow<DemoState>

    fun togglePlay()                                  // 정지 중이면 처음부터 재생, 재생 중이면 정지
    fun stop()                                        // 정지하고 편안 상태로(엔진 새 세션)
    fun setRestriction(state: RestrictionState)       // injectRestriction
    fun setDebugPanelVisible(visible: Boolean)
}
```

규칙:
1. 재생은 `MockSignalSource(demoCommute, REAL_TIME)` 이고 끝까지 가면 정지 상태로 돌아온다.
2. 화면 회전에서도 재생과 수동 값이 유지된다(ViewModel 이 엔진을 소유).
3. `debugAvailable == false`(디버그 불가 빌드)이면 컨트롤과 패널을 숨기고 `setRestriction` 은 무시한다.
4. 수동 제한 값은 다음 시나리오 제한 신호가 올 때까지 유지된다.

## 호환성

- 내부 모듈 간 계약이다. 필드를 추가하는 변경은 허용하고, 이름이나 의미를 바꾸는 변경은 `:app` 영향 검토 후에 한다.
