# Data Model: PetView

필드와 규칙만 적는다. 구현 코드는 `tasks.md` 와 구현 단계에서 쓴다. 엔진 타입(`PetOutput`, `DrivingEvent`, `MotionTier`, `Mood`, `Direction4`, `TurnState`)은
001 기능의 [data-model.md](../001-driving-event-core/data-model.md) 를 따른다. `[P]` 는 `:pet`, `[A]` 는 `:app`, `[E]` 는 `:engine`, `[S]` 는 `:signal`.

## 입력과 외형 [P]

### PetAppearance (FR-011, FR-016, FR-021)

| 필드 | 설명 | 기본값(값이 없을 때) |
|-|-|-|
| `name` | 표시할 이름 문자열 | "내 차" |
| `bodyColor` | 몸 색(내 차 색, ARGB) | `#4F7CFF`(시안 기본색) |
| `personality` | `Personality` | `STURDY` |

- 규칙: 이름이 비어 있으면 기본값을 쓴다. 입력 값 때문에 예외를 던지지 않는다.

### Personality

`STURDY`(듬직), `LIVELY`(활발), `CUTE`(애교).
- `CUTE`: 볼터치를 항상 그린다. `LIVELY`: 기쁨의 점프 초기 속도가 820(그 외 650).

### AmbientMood (FR-019)

`HOT`, `COLD`, `RAIN`, `QUIET`. 엔진 출력과 별개로 `PetController.setAmbient` 로 넣는다(입력을 만드는 일은 이후 기능). 기본은 없음.

### DisplayMode

`HOME`(앱 홈, 앱 화면 전체). 이번 기능에서는 이 값 하나뿐이다(둥둥이는 제외).

## 화면 상태 [P]

### MotionMode (파생)

엔진 출력의 모션 등급에서 정한다. 입력 값의 의미는 FR-008 을 따른다.

| `MotionTier` | `MotionMode` |
|-|-|
| `FULL` | `FULL` |
| `STATIC` | `STATIC` |
| `ONE_SHOT` | `STATIC` |
| 알 수 없음 | `STATIC` |

초기 모드는 `STATIC`(엔진 출력이 오기 전).

### PetRenderState

그리는 쪽(`PetPainter`)이 읽는 스냅샷 상태. 값이 바뀔 때만 다시 그려진다.

| 필드 | 설명 |
|-|-|
| `mood` | 엔진의 `Mood` (`IDLE`, `HAPPY`, `SQUASHED`, `DIZZY`, `STARTLED`) |
| `ambient` | `AmbientMood?` |
| `direction` | 엔진의 `Direction4?`(찌그러짐 포즈의 기울기 방향) |
| `motion` | `MotionMode` |
| `turnSignal` | `TurnState` |
| `appearance` | `PetAppearance` |
| `viewportWidth`, `viewportHeight` | 폭, 높이(시안의 CSS px 와 같은 논리 단위, dp). 0 이하이면 그리지 않는다 |
| `reducedMotion` | 모션 줄이기 여부 |
| `frame` | 풀 모션에서 프레임마다 증가하는 번호(다시 그리기 트리거). 정적에서는 변하지 않는다 |
| `physics` | 풀 모션일 때의 `PetPhysicsState` 스냅샷. 정적에서는 없음 |

### PetPhysicsState (풀 모션 전용, 시안 `S` 의 물리 부분)

| 필드 | 설명 |
|-|-|
| `ox`, `vx` | 좌우 위치 오프셋과 속도(px) |
| `oy`, `vy` | 점프 높이와 속도(px) |
| `sx`, `sy`, `vsx`, `vsy` | 가로, 세로 스케일과 속도. 제한: `sx` 0.55~1.7, `sy` 0.5~1.6 |
| `lat` | 좌우 반응 `Lateral?` |
| `pen` | 벽 침투량 |
| `z`, `vz` | 앞뒤 위치(유리 +1, 등 뒤 −0.75)와 속도 |
| `press` | 유리에 눌린 정도 0~1 |
| `lon` | 앞뒤 반응 `Longitudinal?` |
| `softBody` | 22점 변위 `d[22]`, 속도 `u[22]`. 변위 제한 −0.32~0.42 |
| `smudge` | 급제동 뒤 유리 자국(시작 시각, 위치, 반지름). 지속 1.8초 |
| `splatPhases` | 유리 번짐 가장자리 위상 4개(난수) |
| `onGlass`, `onSeat` | 접촉 중인지(접촉 순간 한 번만 충격을 주기 위함) |
| `blinkNextSec`, `blinkStartSec` | 눈 깜빡임 시각 |
| `clockSec` | 컨트롤러의 물리 시계(`step` 의 `dt` 누적) |

### Lateral / Longitudinal (물리 반응)

| 반응 | 필드 | 수식(시안) |
|-|-|-|
| `Lateral` | `dir`(+1 오른쪽 패널, −1 왼쪽 패널), `s`(0~1), `force`, `untilSec` | `force = 2600 + 3400·s`, `untilSec = now + 0.5 + 0.8·s` |
| `Longitudinal` | `dir`(+1 급제동, −1 급가속), `s`, `untilSec` | 지속 급제동 `0.45 + 0.75·s`, 급가속 `0.40 + 0.65·s` |

### Particle

`type`(`SPARK`, `SWEAT`, `RAIN`, `Z`, `PUFF`, `GLASS_DROP`), 위치, 속도, `life`, `max`, `size`, `color`. 풀에 최대 60개. 정적과 모션 줄이기에서는 생성하지 않는다.

### StaticPose (정적 등급, 순수 값)

| 필드 | 설명 |
|-|-|
| `bodyColor` | 감정별 혼합 색 (편안 몸 색, 기쁨 `#3DDC84` 0.9, 어지러움 `#F5A524` 0.8, 깜짝 `#FF5A5A` 0.85) |
| `tiltDeg` | 찌그러짐의 좌/우 기울기(±10°, 초안) |
| `scaleX`, `scaleY` | 찌그러짐의 앞(1.08 배), 뒤(0.9 배) 배율(초안) |
| `closedEye` | 방향지시등 쪽 눈을 선으로 감을지(`Side?`) |
| `amberBrow` | 호박색 눈썹을 그릴 쪽(`Side?`, `#FFB020`) |
| `hood` | 우비 실루엣 여부(비) |

규칙: 정적 포즈에는 시간 값이 없다. 같은 입력이면 항상 같은 값이다.

## 데모 [A]

### DemoState

| 필드 | 설명 |
|-|-|
| `playback` | `STOPPED` 또는 `PLAYING` |
| `elapsedMs` | 확인용 시나리오 경과 시간(표시만, ViewModel 타이머) |
| `manualRestriction` | 마지막으로 수동 주입한 제한 상태(`RestrictionState?`) |
| `debugAvailable` | 앱이 디버그 가능 빌드인지. 거짓이면 데모 컨트롤과 확인용 패널을 숨긴다 |
| `debugPanelVisible` | 확인용 패널 표시 여부(촬영 시 숨김) |

규칙: 재생 위치와 수동 값은 화면 회전에도 유지된다(ViewModel).

## 엔진과 신호의 가산 변경 [E][S]

### RawSignal.Tick [S]

`Tick(timestampMs)`. 값 없이 "시간이 여기까지 왔다"만 알린다. 검증: 다른 신호와 같은 시각 순서 규칙(감소하지 않음).
엔진은 `Tick` 으로 이벤트를 만들지 않고 시간만 진행한다(감정 지속 시간 만료, 재발 간격).

### MockSignalSource 틱 [S]

- `tickIntervalMs`(기본 100), `tailMs`(기본 3000). 시나리오의 첫 신호부터 마지막 신호 + `tailMs` 까지 `tickIntervalMs` 간격으로 `Tick` 을 섞어 방출한다.
- 같은 시각이면 신호가 먼저, `Tick` 이 나중이다. 두 재생 속도(`REAL_TIME`, `IMMEDIATE`)가 같은 열을 낸다.
- `tickIntervalMs = 0` 이면 틱을 내지 않는다(기존 동작).

### DrivingEngine.injectRestriction [E]

`injectRestriction(state: RestrictionState)`. 엔진이 마지막으로 처리한 신호 시각(시작 전이면 0)으로 `Restriction` 신호를 만들어 같은 코루틴 경로에서 처리한다.
소스를 거치지 않으므로 소스의 `Tick` 과 순서가 섞이지 않는다.

## 상태 전이 요약

```text
MotionMode:  STATIC --(tier == FULL)--> FULL     (물리를 새로 시작, 프레임 루프 시작)
             FULL   --(tier != FULL)--> STATIC   (물리 초기화, 파티클 제거, 프레임 루프 중지, 정적 포즈)

프레임 루프: 실행 = (MotionMode == FULL) && (생명주기 >= STARTED)
            그 외에는 취소. 재개 시 첫 프레임 dt = 0.

반응:       없음 --(HARD_CORNER)--> Lateral --(untilSec 경과)--> 해제 충격 --> 없음
            없음 --(HARD_BRAKE / HARD_ACCEL)--> Longitudinal --(untilSec 경과)--> 해제 충격 --> 없음
            (STATIC 에서는 이벤트를 무시)
```
