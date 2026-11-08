# Contract: 엔진 공개 API (`:engine`)

`:pet`, `:app`, 테스트가 엔진을 쓰는 방법이다. 엔진은 `:signal` 의 타입만 받고 SDK 타입을 노출하지 않는다.

## 동기 코어

```kotlin
class EngineCore(config: EngineConfig = EngineConfig()) {
    /** 신호 하나를 처리한다. 결과에는 이 신호로 확정된 이벤트와 최신 출력이 들어 있다. */
    fun process(signal: RawSignal): EngineStep

    /** 신호가 없어도 시간을 진행시킨다(감정 지속 시간 만료 확인). 시간은 되돌릴 수 없다. */
    fun advanceTo(timeMs: Long): EngineStep

    /** 현재 주행 기록. 진행 중인 주행이 없으면 null. */
    fun currentTrip(): Trip?

    /** 지금까지 끝난 주행 기록(최신이 마지막). */
    fun completedTrips(): List<Trip>
}

data class EngineStep(
    val events: List<DrivingEvent>,   // 이 호출에서 확정된 이벤트(없으면 빈 목록)
    val output: PetOutput,            // 호출 직후의 출력
)
```

규칙:
1. **결정적:** 같은 설정과 같은 신호 열이면 `process`/`advanceTo` 결과가 항상 같다. 벽시계, 난수, 스레드에 의존하지 않는다.
2. **단일 스레드 전제:** `EngineCore` 는 스레드 안전하지 않다. 한 번에 한 호출자가 쓴다(래퍼가 보장).
3. **시간은 되돌리지 않는다:** `timestampMs` 가 직전보다 작은 신호는 무시하고 `EngineStep` 의 `events` 를 비워서 돌려준다(예외를 던지지 않는다).
4. **출력은 변화 시에만 다르다:** `output` 은 항상 최신값을 담는다. 래퍼가 값이 바뀔 때만 내보낸다.
5. **예외 없음:** 잘못된 값(음수 속도 등)은 해당 신호를 버린다. 엔진은 입력 때문에 예외를 던지지 않는다.

## Flow 래퍼

```kotlin
class DrivingEngine(
    source: VehicleSignalSource,
    scope: kotlinx.coroutines.CoroutineScope,
    config: EngineConfig = EngineConfig(),
) {
    /** 최신 출력. 구독하면 마지막 값을 즉시 받는다. 값이 바뀔 때만 갱신된다. */
    val output: kotlinx.coroutines.flow.StateFlow<PetOutput>

    /** 확정된 이벤트 스트림(로그, 음성, 기록용). 구독 전의 이벤트는 받지 않는다. */
    val events: kotlinx.coroutines.flow.SharedFlow<DrivingEvent>

    fun start()
    fun stop()
}
```

규칙:
1. `start()` 는 `source.start()` 를 호출하고 신호를 한 건씩 `EngineCore.process` 로 보낸다. `stop()` 은 둘 다 멈춘다.
2. 모든 `EngineCore` 호출은 한 코루틴에서 순서대로 한다.
3. 메인 스레드 전환은 구독하는 쪽(UI)의 책임이다. 래퍼는 호출한 `scope` 의 디스패처에서 동작한다.

## 출력 타입

```kotlin
data class PetOutput(
    val mood: Mood,
    val intensity: Float,          // 0..1
    val direction: Direction4?,
    val tier: MotionTier,
    val turnSignal: TurnState,
    val atMs: Long,
)
```

의미와 규칙은 [data-model.md](../data-model.md) 를 따른다. 소비하는 쪽(`:pet`)이 지켜야 할 것:
- `tier == STATIC` 이면 반복 모션, 물리 반응, 파티클을 쓰지 않는다(헌장 I).
- 이 기능은 `ONE_SHOT` 을 기본으로 내보내지 않는다(`allowOneShot == false`).

## 호환성

- 이 API 는 내부 모듈 간 계약이다. 필드를 추가하는 변경은 허용하고, 이름이나 의미를 바꾸는 변경은 `:pet`, `:app` 영향 검토 후에 한다.
