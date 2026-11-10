# Data Model: 실차 신호 연결

**Feature**: [spec.md](./spec.md) | **Research**: [research.md](./research.md)

기존 타입(`RawSignal`, `Speed`, `ParkedState`, `Restriction`, `Tick`, `RestrictionState`)은 그대로 쓴다. 새 타입만 적는다.
모듈은 `:signal`(신호와 변환), `:app`(모드, 진단 표시)로 나뉜다. `:engine`, `:pet` 은 바뀌지 않는다.

## `:signal` — 차량 접근 경계 (`com.example.dashpet.signal.car`)

### CarEvent (sealed, 차량 쪽에서 올라오는 원시 사건)

`AndroidCarGateway` 가 만들고 `CarSignalSource` 가 받는다. Android 타입을 담지 않는다.

| 하위 타입 | 필드 | 설명 |
|-|-|-|
| `SpeedChanged` | `mps: Float` | `PERF_VEHICLE_SPEED` 값(m/s). 변환 전 값이라 비정상 값이 있을 수 있다 |
| `GearChanged` | `gear: Int` | `GEAR_SELECTION` 값(`VehicleGear`) |
| `RestrictionChanged` | `requiresDo: Boolean`, `activeRestrictions: Int` | `CarUxRestrictions` 의 두 값 |
| `Ready` | - | 차량 서비스가 준비됨. 게이트웨이가 이어서 현재 값을 위 세 이벤트로 한 번씩(속도, 기어, 제한 순) 내보낸다 |
| `Lost` | - | 차량 서비스가 끊김 |
| `AvailabilityChanged` | `signal: CarSignal`, `availability: SignalAvailability` | 신호 하나를 쓸 수 있는지(권한, 지원 여부, 오류) |

`enum CarSignal { SPEED, GEAR, RESTRICTION }`.

### CarLinkState (enum, 연결 상태)

`IDLE`(수신 안 함) → `CONNECTING` → `CONNECTED` | `UNAVAILABLE`(차량 서비스 없음) ; `CONNECTED` → `LOST` → `CONNECTED`(복구).
수신을 멈추면 항상 `IDLE` 로 돌아간다.

### SignalAvailability (신호별 쓸 수 있는지)

| 값 | 의미 |
|-|-|
| `UNKNOWN` | 아직 확인 전 |
| `OK` | 값을 받고 있다 |
| `NO_PERMISSION` | 권한이 거부됨(속도) |
| `UNSUPPORTED` | 이 차량이 지원하지 않음(속성 설정 없음) |
| `ERROR` | 읽다가 실패함 |

### CarSignalConfig (설정, 한 곳)

| 필드 | 기본값 | 설명 |
|-|-|-|
| `tickIntervalMs` | 250 | 시간 진행 신호 간격(0 이하면 끔) |
| `speedRate` | `SpeedRate.UI` | 속도 구독 주기. `enum SpeedRate { ONCHANGE, NORMAL, UI, FAST }` 를 게이트웨이가 `CarPropertyManager.SENSOR_RATE_*`(UI = 5.0)로 옮긴다. `android.car` 를 이 타입에 들이지 않으려고 enum 으로 뒀다. `[확인 필요]` 엔진 가정과 대조 |

### CarGateway (인터페이스, 차량 서비스 접근 한 겹)

`fun events(): Flow<CarEvent>` 하나다. 수집하면 연결하고 수집을 끝내면 모든 리스너 해제와 연결 해제를 하는 차가운 흐름이다. 현재 값 읽기와 신호별 가용 상태도 이 흐름의 이벤트(`SpeedChanged` 등, `AvailabilityChanged`)로 나온다.
구현은 `AndroidCarGateway`(실제 `android.car`, `internal`)와 테스트용 가짜(`FakeCarGateway`, `src/test`, `fail()` 로 실행 중 실패를 만들 수 있다). 상세는 [contracts/car-signal-source.md](./contracts/car-signal-source.md).

### 변환 규칙 (`CarSignalMapper`, 순수 함수)

| 입력 | 출력 | 규칙 |
|-|-|-|
| `SpeedChanged(mps)` | `Speed` 또는 null | 유한하고 0 초과면 그대로. 0 이하이고 `-SPEED_EPSILON_MPS`(1e-3 m/s, `[확인 필요]`) 이상이면 0 으로 보정(`-0.0` 포함). 그 밖의 음수, NaN, 무한대는 버림(null). 버려진 값은 중복 제거 기준에 영향이 없다 |
| `GearChanged(gear)` | `ParkedState` 또는 null | `gear == 0`(GEAR_UNKNOWN)이면 null. `4`(GEAR_PARK)이면 `parked=true`. 그 밖의 값은 `parked=false` |
| `RestrictionChanged(requiresDo, active)` | `Restriction` 또는 null | `requiresDo || active != 0` 이면 `ACTIVE`, 아니면 `NONE` |
| `Lost` / 게이트웨이 실패 | `Restriction(UNKNOWN)` 또는 null | 이미 `UNKNOWN` 을 보냈으면 null(한 번만) |
| (시작 직후 제한을 아직 못 받음) | 없음 | 엔진의 기본값(`UNKNOWN`)을 그대로 쓴다. 소스가 따로 신호를 내지 않는다 |

- **중복 제거**: 직전에 보낸 같은 종류의 값(속도, 주차 여부, 제한 상태)과 같으면 null. 세션을 새로 시작하면(매퍼를 새로 만들면) 같은 값도 한 번 만든다. `Lost` 뒤에는 마지막으로 보낸 값이 `UNKNOWN` 이므로 복구 직후 같은 값이 와도 다시 보낸다.
- **시각**: `max(직전 시각, clock() - 시작 시각)`. 매 호출에서 시계를 한 번 읽는다(null 을 돌려줄 때도). 같은 시각은 허용, 감소는 없다. `lastMs` 로 방금 쓴 시각을 다시 읽는다.
- 주차 여부와 제한은 서로의 입력이 아니다(FR-004).

### CarSignalSource (VehicleSignalSource 구현)

- 생성: `CarSignalSource(gateway, clock, config, log)`. 앱은 `CarSignalSource.create(context, log, config)` 를 쓴다(`:app` 이 `android.car` 를 직접 보지 않도록 게이트웨이와 `SystemClock::elapsedRealtime` 를 여기서 고른다). `SPEED_PERMISSION` 상수(`"android.car.permission.CAR_SPEED"`)도 여기서 노출한다.
- 상태: 소스를 켜고 끄는 상태만. 연결, 리스너, 매퍼, 시각은 신호 흐름이 수집되는 동안에만 산다.
- 출력: `signals` 흐름(`Speed`, `ParkedState`, `Restriction`, `Tick`).
- 부수 출력: `diagnostics: StateFlow<SignalDiagnostics>`.

### SwitchableSignalSource

현재 선택된 `VehicleSignalSource` 의 신호만 내보낸다(`flatMapLatest`). `select(source)` 로 바꾸면 이전 소스를 `stop()` 하고 수집이 새 소스로 옮겨 간다. 같은 소스면 무시한다.

## `:signal` — 진단 (`SignalDiagnostics`, 불변 값)

| 필드 | 타입 | 설명 |
|-|-|-|
| `link` | `CarLinkState` | 연결 상태 |
| `restriction` | `RestrictionState` | 마지막으로 엔진에 보낸 제한 |
| `activeRestrictions` | `Int?` | 마지막으로 받은 비트(없으면 null) |
| `speedMps` | `Float?` | 마지막으로 보낸 속도 |
| `parked` | `Boolean?` | 마지막으로 보낸 주차 여부 |
| `speedAvailability` / `gearAvailability` / `restrictionAvailability` | `SignalAvailability` | 신호별 상태와 이유 |
| `lastUpdateMs` | `Long?` | 마지막으로 **값이 실제로 바뀐** 시각(소스 시작 기준 ms). 같은 값의 반복에는 움직이지 않는다 — 움직이면 정적 상태에서도 화면이 계속 다시 그려진다. 제한이 오래 변하지 않아도 연결 상태(`link`)가 정상임을 함께 보여 준다(스펙 US3-4) |

## `:app`

### SignalMode (enum)

`VEHICLE`(기본), `DEMO`. 한 번에 하나만 활성.

### DemoState (기존 확장, 002 data-model 의 `DemoState`)

| 필드 | 변경 |
|-|-|
| `mode: SignalMode` | 추가. 기본 `VEHICLE` |
| `diagnostics: SignalDiagnostics?` | 추가. `VEHICLE` 모드에서만 값이 있다. `DemoViewModel` 이 생성자 인자 `diagnostics: StateFlow<SignalDiagnostics>?` 로 받는다 |
| 기존 `playback`, `elapsedMs`, `manualRestriction`, `debugAvailable`, `debugPanelVisible` | 그대로. `VEHICLE` 모드에서는 재생과 수동 제한이 동작하지 않는다 |

### 상태 전이

```text
앱 시작 ──▶ VEHICLE(IDLE) ──화면에 보임──▶ VEHICLE(CONNECTING → CONNECTED)
VEHICLE ──화면에서 사라짐──▶ VEHICLE(IDLE)            (수신 정리)
VEHICLE ──모드 전환──▶ DEMO                           (engine.stop → 소스 선택 → engine.start)
DEMO ──모드 전환──▶ VEHICLE                           (재생 정지, 엔진 새 세션, 현재 값부터)
VEHICLE(CONNECTED) ──서비스 끊김──▶ VEHICLE(LOST) ──복구──▶ VEHICLE(CONNECTED)
VEHICLE ──CAR_SPEED 권한 부여──▶ 수신 재시작(속도 포함)
```

## 검증 규칙 요약

- 엔진에 들어가는 `Speed` 는 항상 유한하고 0 이상이다(`Speed.init` 이 강제, 매퍼는 그 전에 걸러낸다).
- 같은 스트림 안의 `timestampMs` 는 감소하지 않는다.
- 제한은 `requiresDo`, `active` 만으로 정해지고 속도와 기어는 입력이 아니다.
- 수신을 멈추면 `link == IDLE` 이고 리스너와 `Car` 연결이 모두 정리돼 있다.
