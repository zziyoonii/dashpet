# Contract: 신호 소스 (`:signal`)

`:engine` 이 신호를 받는 유일한 경로다. 소스가 목업이든 실제 차량이든 엔진은 알지 못한다(FR-022).

## 인터페이스

```kotlin
interface VehicleSignalSource {
    /** 시간순 신호 스트림. 같은 스트림 안에서 timestampMs 는 감소하지 않는다. */
    val signals: kotlinx.coroutines.flow.Flow<RawSignal>

    fun start()
    fun stop()
}
```

신호 타입과 검증 규칙은 [data-model.md](../data-model.md) 의 `RawSignal` 을 따른다.

## 소스가 지켜야 할 규칙

1. **시간순:** 한 스트림 안에서 `timestampMs` 는 감소하지 않는다. 같은 시각이면 보내는 순서가 처리 순서다.
2. **값 범위:** 속도는 0 이상. 방향은 0 이상 360 미만(어긋나면 소스가 정규화한다).
3. **누락 표현:** 값을 알 수 없으면 해당 신호를 보내지 않는다. 가짜 값(예: 속도 0)을 채워 넣지 않는다. 엔진은 간격이 2초를 넘는 샘플을 버린다.
4. **제한 상태:** `Restriction(UNKNOWN)` 을 보내거나 아예 보내지 않으면 엔진은 정적으로 처리한다.
5. **수명주기:** `start()` 전에는 방출하지 않고, `stop()` 뒤에는 방출을 멈춘다. `stop()` 후 `start()` 는 처음부터 다시 시작한다.
6. **SDK 격리:** 실제 SDK 를 쓰는 구현체는 `:signal` 안에만 둔다. SDK 타입은 이 인터페이스 밖으로 새지 않는다.

## 이 기능에서 제공하는 구현

### MockSignalSource

```kotlin
class MockSignalSource(scenario: Scenario, pacing: Pacing = Pacing.REAL_TIME) : VehicleSignalSource
```

- `Pacing.REAL_TIME`: 신호 시각 차이만큼 기다린 뒤 방출한다(데모, 영상 촬영).
- `Pacing.IMMEDIATE`: 기다리지 않고 순서대로 방출한다(테스트).
- 어느 쪽이든 내용과 순서는 같다(같은 시나리오 → 같은 스트림).
- 신호 목록은 `scenario.expand()` 가 만든다. 속도 램프와 방향 회전은 10Hz(100ms) 간격 값으로 풀린다.

### Scenario 와 DSL

```kotlin
fun scenario(name: String, build: ScenarioBuilder.() -> Unit): Scenario
```

`ScenarioBuilder` 가 제공하는 단계: `parked(Boolean)`, `brakePedal(Boolean)`, `turnSignal(TurnState)`, `warning(WarningType, active)`,
`laneChange(Side)`, `restriction(RestrictionState)`, `speed(mps)`, `heading(deg)`, `speedRamp(fromKmh, toKmh, durationMs)`, `headingTurn(deltaDeg, durationMs)`. 각 단계는 `at(ms) { ... }` 안에서 쓴다.

### 제공 시나리오

- `DemoScenarios.demoCommute`: 개발 명세서 9장의 데모 출근 시나리오(52초). 기대 결과는 [quickstart.md](../quickstart.md) 와 `DemoScenarioTest` 가 고정한다.

## 이 기능에서 제공하지 않는 것

- 실제 차량 소스(AAOS 속도, SDK 방향지시등 등): 별도 기능.
- JSON 시나리오 로더: [research.md](../research.md) R1.
- 여러 소스 합치기(`Composite`): 필요해지는 기능에서 추가.
