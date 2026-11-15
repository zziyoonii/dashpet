# Research: 실차 신호 연결

**Feature**: [spec.md](./spec.md) | **Date**: 2026-11-10

조사는 문서(`docs/`, `raw/`), 설치된 SDK 스텁(`platforms/android-34/optional/android.car.jar`), 실행 중인 에뮬레이터
(`Pleos_Connect_v2.1.0`, API 34)에서 직접 확인했다. 확인하지 못한 것은 `[확인 필요]` 로 남기고 해결 방법을 적었다(헌장 VI).

## R1. `android.car` 를 어떻게 컴파일하고 실행하나

- **확인**: `platforms/android-34/optional/android.car.jar` 가 있고 `optional.json` 에 `"name": "android.car"` 로 등록돼 있다.
  `Car`, `CarPropertyManager`, `CarUxRestrictionsManager` 클래스가 들어 있다. 에뮬레이터의 `BOOTCLASSPATH` 에
  `/apex/com.android.car.framework/javalib/android.car-module.jar` 가 있어 런타임에는 앱 클래스패스에 따로 넣을 필요가 없다.
  `pm list libraries` 에는 `android.car` 가 없으므로 `<uses-library>` 는 쓰지 않는다.
- **Decision**: `:signal` 에서 컴파일 전용으로 `android.car` 를 참조한다. AGP 의 `useLibrary("android.car")` 를 먼저 시도하고, AGP 9.5 에서
  동작하지 않으면 `compileOnly(files("<ANDROID_HOME>/platforms/android-34/optional/android.car.jar"))` 로 대체한다(경로는 `local.properties` 의 `sdk.dir`).
  `android.car` 를 만지는 파일은 `AndroidCarGateway.kt` 한 개로 제한한다.
- **Rationale**: Pleos SDK 에는 속도와 주행 제한 API 가 없다(`docs/data-source-findings.md`, `docs/api-inventory.md`). AAOS 표준만이 문서가 권하는 경로다(AGENTS.md 2절).
- **Alternatives**: (a) 리플렉션: 타입 안전성이 없고 R8 에서 깨지기 쉽다. (b) Pleos Vehicle SDK 의 deprecated `Gear`: 속도와 제한 상태가 없어 단독으로 불가. 표준 API 가 불허되면 대체 경로로만 쓴다.
- **`[확인 필요]`**: `useLibrary("android.car")` 가 AGP 9.5 의 DSL 에서 되는지는 첫 빌드에서 확인한다.

## R2. 어떤 값을 어디서 읽나

| 신호 | 출처 | 값 | 비고 |
|-|-|-|-|
| 속도 | `CarPropertyManager`, `VehiclePropertyIds.PERF_VEHICLE_SPEED`(291504647) | float, m/s | 클러스터 표시 속도는 `PERF_VEHICLE_SPEED_DISPLAY`(291504648) 로 따로 있다. 문서(AGENTS.md 2절)는 `PERF_VEHICLE_SPEED` 를 지정한다 |
| 기어 | `CarPropertyManager`, `VehiclePropertyIds.GEAR_SELECTION`(289408000) | int, `VehicleGear.GEAR_*` | `CURRENT_GEAR`(289408001) 는 실제 변속기 상태. 운전자가 고른 값인 `GEAR_SELECTION` 을 쓴다 |
| 제한 | `CarUxRestrictionsManager.registerListener` / `getCurrentCarUxRestrictions` | `isRequiresDistractionOptimization()`, `getActiveRestrictions()` | 지역별 규칙은 플랫폼이 이미 적용해 내려준다(`raw/dev-guide.md` 1032행) |

- `VehicleGear` 값: `GEAR_UNKNOWN`=0, `GEAR_NEUTRAL`=1, `GEAR_REVERSE`=2, `GEAR_PARK`=4, `GEAR_DRIVE`=8(스텁에서 확인).
- **Decision**: 속도 = `PERF_VEHICLE_SPEED`, 기어 = `GEAR_SELECTION`, 제한 = `CarUxRestrictionsManager`. `GEAR_SELECTION` 은 P 일 때만 `parked=true`.
- **Rationale**: 헌장 I 은 제한 상태를 1순위로, 속도와 주차는 보조로 둔다. 기어는 Virtual Console 에서 바꾸는 값(`docs/03` 540행)과 같은 개념이다.
- **Alternatives**: `PARKING_BRAKE_ON`, `IGNITION_STATE` 를 주차 판단에 추가. 스펙 Assumptions 에서 이번 범위 밖으로 정했다.

## R3. 주행 제한을 `RestrictionState` 로 바꾸는 규칙

- **확인(에뮬레이터)**: `dumpsys car_service --services CarUxRestrictionsManagerService` 에서 기준 설정은 상태별로
  `parked: DO=false, 0x0`, `idling: DO=true, 0x50`, `unknown: DO=true, 0x1ff` 이고, 현재 값은 `DO: true UxR: 80`(0x50) 이었다.
  `CarDrivingStateService` 의 로그에서 상태가 계속 바뀌는 것도 보였다.
- **Decision**: `requiresDistractionOptimization || activeRestrictions != 0` 이면 `ACTIVE`, 둘 다 아니면 `NONE`.
  값을 읽지 못하거나 연결이 없으면 `UNKNOWN`. 속도와 기어는 이 판단에 쓰지 않는다(FR-004).
- **Rationale**: 한쪽만 보면 비트가 하나라도 걸린 상태를 `NONE` 으로 오판할 수 있다. 안전 쪽으로 합친다(헌장 I). 지역 분기를 두지 않는다(FR-011).
- **Alternatives**: `activeRestrictions` 의 특정 비트(VIDEO, CONTENT 등)만 본다 — 지역과 앱 분류에 따라 달라 하드코딩이 된다.

## R4. 권한

- **확인(에뮬레이터)**: `android.car.permission.CAR_SPEED` 는 `prot=dangerous`(런타임 요청 필요), `android.car.permission.CAR_POWERTRAIN` 은 `prot=normal`.
  `pleos.car.permission.CAR_SPEED`, `CAR_POWERTRAIN` 은 이 이미지의 `pm list permissions` 에 **없다**(`CAR_DRIVE` 등 일부만 있음). 문서는 pleos 권한 목록에 `CAR_SPEED` 를 적지만(`raw/dev-guide.md` 247행)
  `PERF_VEHICLE_SPEED` 에 필요한 권한 문자열은 "명시 없음"이다(`docs/03` 436, 603행).
- **Decision**: manifest 에 `android.car.permission.CAR_SPEED`, `android.car.permission.CAR_POWERTRAIN` 두 개만 선언한다. 제한 상태는 권한 없이 읽는다고 가정하고 첫 실행에서 확인한다.
  `CAR_SPEED` 는 런타임에 요청하고, 거부되면 속도만 생략한다(FR-010).
- **Rationale**: 헌장 IV 는 `pleos.car.permission.*` 만 선언하라고 하지만, 표준 API 사용을 허용하는 조건 자체가 `[확인 필요]` 이고 pleos 쪽에는 해당 권한이 이미지에 없다. 최소 두 개로 한정하고 Complexity Tracking 에 기록한다.
- **`[확인 필요]`**: ① 대회 측이 `android.car.permission.*` 선언과 승인을 허용하는지. ② `CAR_SPEED` 런타임 요청이 이 이미지에서 다이얼로그로 뜨는지, 아니면 `pm grant` 가 필요한지. ③ `CarUxRestrictionsManager` 가 정말 권한 없이 동작하는지.

## R5. 연결과 수명주기

- **확인**: `Car.createCar(Context, Handler, long, CarServiceLifecycleListener)` 가 있고 리스너는 `onLifecycleChanged(Car, boolean ready)` 하나다. `Car.disconnect()`, `isConnected()` 도 있다.
- **Decision**: 신호 흐름이 수집되는 동안에만 `Car` 를 만들고 연결한다. `ready=true` 면 현재 값을 읽고 리스너를 등록하며, `ready=false`(서비스 끊김)이면 `Restriction(UNKNOWN)` 을 보내고 등록을 해제한 뒤 다음 `ready=true` 에서 다시 시작한다.
  수집이 끝나면(`awaitClose`) 리스너를 모두 해제하고 `disconnect()` 한다. 다시 시작할 때는 새 `Car` 를 만든다(AGENTS.md 3절, 헌장 IV).
- **Rationale**: 연결 수명을 흐름의 수집 수명에 묶으면 `start()`/`stop()` 호출 순서가 어긋나도 누수가 생기지 않고(SC-007), 엔진이 "수집 먼저, 소스 시작 나중" 순서로 쓰는 기존 규칙과 맞는다.
- **Alternatives**: 앱 시작 시 한 번 연결하고 계속 유지 — 백그라운드 자원 사용과 해제 누락 위험이 있다.

## R6. 스레드, 시각, 속도 주기

- **스레드**: 콜백 스레드는 문서에 명시가 없다(`[확인 필요]`). 어느 스레드에서 와도 안전하게 한 곳(락과 단일 채널)에서 시각을 붙이고 순서를 지킨 뒤 흐름으로 보낸다. 화면 갱신은 기존처럼 구독하는 쪽이 메인 스레드로 옮긴다.
- **시각**: 시계는 주입 가능한 함수(`() -> Long`)로 받고 앱에서는 `SystemClock.elapsedRealtime()` 을 쓴다(벽시계 미사용). 소스 시작 시점을 0 으로 하고, 직전 시각보다 작아지면 직전 값으로 올린다(FR-008).
- **속도 주기**: `CarPropertyManager.SENSOR_RATE_UI` 로 구독한다. 엔진의 가속 판단이 속도 간격에 민감한지는 001 의 `EventConfig` 가정과 대조해야 한다(`[확인 필요]`). 주기는 설정값(`CarSignalConfig.speedRate`)으로 빼 둔다.
- **Tick**: 신호가 없는 구간에서 엔진 시간이 가도록 250 ms 마다 `Tick` 을 낸다(목업은 100 ms). 모션 루프와는 무관하고, 신호 수집 중에만 돈다.

## R7. 모드 전환

- **Decision**: `:signal` 에 `SwitchableSignalSource` 를 두고 현재 소스(실차 또는 목업)의 신호만 엔진에 넘긴다. 전환은 `engine.stop()` → 소스 선택 → `engine.start()` 순서로 한다(새 세션, 엔진 상태 초기화).
  `DemoViewModel` 은 이름은 유지하고 `SignalMode` 상태와 생명주기 연동(`onForeground`, `onBackground`)만 더한다.
- **Rationale**: 엔진과 `PetController.collectFrom(engine)` 연결을 바꾸지 않아도 된다. 기존 `PassiveSource` 패턴(재생 시작은 ViewModel 이 직접)을 유지해 데모 동작이 안 바뀐다(FR-022).
- **Alternatives**: 모드마다 엔진을 새로 만든다 — 화면이 `collectFrom` 을 다시 걸어야 하고 회전 시 상태 보존이 복잡해진다.

## R8. 에뮬레이터 검증 경로

- **확인**: 이 에뮬레이터에는 `hmg_car_service` 서비스가 **없다**(`cmd: Can't find service: hmg_car_service`). 그래서 AGENTS.md 4절의 `driving_regulation on|off` 명령은 쓸 수 없다.
  `cmd car_service get-property-value` 는 "requires non-user build" 로 막혀 있다. 반면 `dumpsys car_service --services CarUxRestrictionsManagerService` 와 `CarDrivingStateService` 는 읽힌다.
  Virtual Console(`http://127.0.0.1:8088/`)은 지금 HTTP 200 으로 응답한다(앞서 한 번 응답이 없었던 건 부팅 직후였다).
- **Decision**: 기어와 속도는 Virtual Console 에서 바꾸고, 앱이 받은 값은 앱의 진단 패널과 `logcat` 으로, 플랫폼 쪽 상태는 `dumpsys car_service` 로 대조한다.
- **`[확인 필요]`**: Virtual Console 에 속도를 직접 바꾸는 카드가 있는지(`docs/05` 271행에 "불명확").

## R9. 미결 항목의 결과 (구현 후, 2026-11-10)

| # | 항목 | 영향 | 결과 |
|-|-|-|-|
| 1 | 표준 API/권한 허용 여부 | 실제 제출 가능성 | **미결.** 대회 측 확인이 필요하다. 신호 소스 인터페이스 뒤에서 교체 가능. 아래 "새로 알게 된 것"의 위치 권한 그룹 문제도 함께 확인해야 한다 |
| 2 | `CAR_SPEED` 런타임 부여 방식 | 속도 신호 | **해결.** 앱이 실차 모드로 열릴 때 런타임 다이얼로그로 요청하고, 허용하면 소스를 재시작해 속도가 들어오며, 거부하면 `NO_PERMISSION` 으로 계속 동작한다. 개발 중 부여는 `adb shell pm grant --user 10 com.example.dashpet android.car.permission.CAR_SPEED`(AAOS 멀티 유저라 `--user 10` 필요, `installDebug` 로 다시 설치하면 사라짐) |
| 3 | 제한 상태를 권한 없이 읽을 수 있는가 | 핵심 신호 | **해결.** `CAR_SPEED` 가 부여되지 않은 채로 제한 상태를 읽고 변화를 받았다. 제한 읽기에는 권한이 필요 없다 |
| 4 | 콜백 스레드 | 동시성 | **대응함, 스레드 자체는 미확인.** 세션 전용 `HandlerThread` 에서 받고 소스가 락 안에서 시각을 붙여 순서를 지킨다. 다른 스레드에서 동시에 400건을 넣는 테스트로 유실과 순서를 확인했다 |
| 5 | 속도 주기와 엔진 가정 | 가속 이벤트 품질 | **부분 확인.** `SENSOR_RATE_UI`(5 Hz)로 구독하고 같은 값은 걸러낸다. 가속 이벤트 품질은 가속 장면이 있는 실차 주행에서 봐야 한다(`[확인 필요]`, `CarSignalConfig.speedRate` 로 조정) |
| 6 | `useLibrary("android.car")` 동작 | 빌드 | **해결.** AGP 9.5 에서 `android { useLibrary("android.car") }` 만으로 컴파일된다. 릴리즈(R8) 빌드도 성공 |

### 새로 알게 된 것

- **권한이 없으면 속도 속성이 "없는 것"처럼 보인다.** `getCarPropertyConfig(PERF_VEHICLE_SPEED)` 가 null 이고 `getProperty` 는 `SecurityException`. 그래서 권한 확인을 설정 확인보다 먼저 한다.
- **`CAR_SPEED` 는 `android.permission-group.LOCATION` 그룹이다.** 다이얼로그가 "이 기기의 위치 정보에 액세스하도록 허용하시겠습니까?" 로 보인다. 앱은 위치를 읽지 않는다. 헌장 V(개인정보)와 심사 대응을 위해 대회 측에 확인해야 한다.
- **Virtual Console 은 HTTP 로 조작할 수 있다.** `PUT /vehicle/gear?gear=Park|Reverse|Neutral|Drive`, `PUT /vehicle/controls?speed=<km/h>&accelerator=0&brake=0&steering=0`, 상태는 `GET /vehicle/state`. 브라우저 탭이 없으면 `emulator_inactive` 가 오므로 페이지가 1초마다 보내는 `POST /browser/session?client=<id>` 하트비트가 필요하다.
- **분할 윈도우에서는 Home, Back, GNB 로 다른 앱을 열어도 이 앱이 계속 보인다.** `onStop` 을 보려면 전체 화면 윈도우(`am start --windowingMode 1 ...`)로 덮는다.
- **설정 변경에서 Activity 가 재생성되면 수신이 끊긴다.** `configChanges` 로 막았다(R5 의 "수집 수명에 묶는다"와 함께 필요).
- **같은 값이 자주 오므로 진단을 값이 바뀔 때만 갱신해야 한다.** 아니면 정적 상태에서도 CPU 16%, 약 9fps.
