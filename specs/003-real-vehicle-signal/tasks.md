---

description: "실차 신호 연결 구현 작업 목록"
---

# Tasks: 실차 신호 연결

**Input**: `specs/003-real-vehicle-signal/` 의 `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/car-signal-source.md`, `quickstart.md`

**Prerequisites**: plan.md, spec.md (완료). 001(`:signal`, `:engine`)과 002(`:pet`, 데모 화면)가 완료되어 있어야 한다(브랜치 `002-pet-view` 의 내용 위에서 시작).

**Tests**: 변환 규칙, 신호 소스, 전환 소스, ViewModel 은 **테스트를 먼저 쓴다**(헌장 III, 실패를 확인한 뒤 구현). `AndroidCarGateway` 는 `android.car` 를 직접 쓰므로 JVM 에서 검증할 수 없고, 에뮬레이터에서 `quickstart.md` 로 확인한 결과를 `checklists/verification-log.md` 에 남긴다.

**Organization**: 스토리별로 묶었다. US1~US3 은 신호 연결 자체(P1), US4~US5 는 앱 쪽 품질(P2)이다.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 다른 파일이고 아직 끝나지 않은 작업에 의존하지 않아 병렬로 할 수 있음
- **[Story]**: 스펙의 사용자 스토리(US1~US5)

## Path Conventions

- `signal/...`, `app/...` 는 각 모듈의 `src/main/java/com/example/dashpet/<경로>/` 를 줄여 쓴 것이다. 테스트는 `src/test/java/com/example/dashpet/<경로>/` 이다.
  - `:signal` → `.../signal/`(차량 관련은 `.../signal/car/`), `:app` → `.../`(패키지 `com.example.dashpet`, 화면은 `.../ui/`)
  - `:engine`, `:pet` 은 바꾸지 않는다(FR-022).
- 실행 환경: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"` 후
  `./gradlew :signal:testDebugUnitTest :engine:testDebugUnitTest :pet:testDebugUnitTest :app:testDebugUnitTest --console=plain`
- 에뮬레이터: `export ADB=~/Library/Android/sdk/platform-tools/adb`. 이 에뮬레이터에는 `hmg_car_service` 가 없어 `driving_regulation` 명령을 쓰지 않는다(research R8).

**plan.md 와의 차이 (구현 중 바로잡는 것)**: 현재 값 읽기는 소스가 아니라 `AndroidCarGateway` 가 `Ready` 직후 `SpeedChanged`, `GearChanged`, `RestrictionChanged` 로 내보내는 것으로 구현한다.
`contracts/car-signal-source.md` 2절과 `data-model.md` 의 `CarGateway` 설명은 T041 에서 이 구현에 맞게 고친다. 소스는 이벤트를 받는 쪽만 알면 되어 가짜 게이트웨이 테스트가 단순해진다.

**주의 (스레드)**: 차량 서비스 콜백이 오는 스레드는 문서에 없다(`[확인 필요]`). `CarSignalSource` 는 락과 단일 채널로 시각을 붙이고 순서를 지켜서 어느 스레드에서 와도 안전해야 한다. 화면 갱신은 구독하는 쪽이 메인 스레드로 옮긴다.

**주의 (UI)**: `DemoControls` 는 `debugAvailable` 이 거짓이면 아무것도 그리지 않는다(기존 동작). 진단 패널과 모드 선택도 같은 규칙을 따른다. 릴리즈 빌드에서는 컨트롤이 없고 기본 모드(실차)로 동작한다.

---

## Phase 1: Setup (공통 설정)

**Purpose**: `android.car` 를 컴파일할 수 있게 하고 권한과 검증 기록 틀을 만든다

- [X] T001 `signal/build.gradle.kts` 에서 `android { useLibrary("android.car") }` 로 `android.car` 를 컴파일 전용으로 참조하게 한다. AGP 9.5 에서 동작하지 않으면 `compileOnly(files("<local.properties 의 sdk.dir>/platforms/android-34/optional/android.car.jar"))` 로 바꾼다. `signal/src/main/java/com/example/dashpet/signal/car/AndroidCarGateway.kt` 에 `android.car.Car` 를 import 하는 빈 `internal class AndroidCarGateway` 를 임시로 만들어 `./gradlew :signal:compileDebugKotlin` 이 통과하는지 확인한다(research R9-6 해결). 어느 방식을 썼는지 주석으로 남긴다.
- [X] T002 [P] `app/src/main/AndroidManifest.xml` 의 `<uses-feature>` 아래에 `<uses-permission android:name="android.car.permission.CAR_SPEED" />` 와 `<uses-permission android:name="android.car.permission.CAR_POWERTRAIN" />` **두 개만** 추가한다. 기존 "pleos.car.permission.* 권한은 …" 주석은 두고, 이 둘이 AAOS 표준 권한이고 대회 측 허용 여부가 `[확인 필요]` 임을 `plan.md` Complexity Tracking 을 가리키는 주석으로 적는다(헌장 IV).
- [X] T003 [P] `specs/003-real-vehicle-signal/checklists/verification-log.md` 를 만든다. 제목, 환경(AVD `Pleos_Connect_v2.1.0`, API 34, Cold boot, RAM 4 GB), `quickstart.md` 의 시나리오 12개 표(결과 칸 비움), `research.md` R9 의 미결 6건 표(결과 칸 비움)를 틀로 둔다. 002 의 `specs/002-pet-view/checklists/verification-log.md` 형식을 따른다.

---

## Phase 2: Foundational (모든 스토리가 기다리는 기반)

**Purpose**: 게이트웨이 경계 타입, 진단 타입, 테스트용 가짜, 모드 전환 소스

**⚠️ CRITICAL**: 이 단계가 끝나기 전에는 어느 스토리도 시작할 수 없다

- [X] T004 `signal/.../car/CarGateway.kt` 를 만든다. `sealed interface CarEvent`(`SpeedChanged(mps: Float)`, `GearChanged(gear: Int)`, `RestrictionChanged(requiresDo: Boolean, activeRestrictions: Int)`, `Ready`, `Lost`, `AvailabilityChanged(signal: CarSignal, availability: SignalAvailability)`), `enum class CarSignal { SPEED, GEAR, RESTRICTION }`, `enum class SignalAvailability { UNKNOWN, OK, NO_PERMISSION, UNSUPPORTED, ERROR }`, `data class CarSignalConfig(tickIntervalMs: Long = 250, speedRate: Float = <CarPropertyManager.SENSOR_RATE_UI 값, [확인 필요] 엔진 가정과 대조>)`, `interface CarGateway { fun events(): Flow<CarEvent> }` 를 정의한다. `events()` 는 수집을 시작하면 연결하고 수집을 끝내면 모든 리스너 해제와 연결 해제를 하는 차가운 흐름이다. Android 타입(`android.car.*`, `Context`)을 이 파일에 쓰지 않는다. `speedRate` 의 기본값은 `android.car` 를 import 하지 않도록 상수 `0` 대신 게이트웨이가 해석하는 enum(`SpeedRate.UI`)으로 둔다.
- [X] T005 [P] `signal/.../car/SignalDiagnostics.kt` 를 만든다. `enum class CarLinkState { IDLE, CONNECTING, CONNECTED, UNAVAILABLE, LOST }` 와 `data class SignalDiagnostics(link: CarLinkState, restriction: RestrictionState, activeRestrictions: Int?, speedMps: Float?, parked: Boolean?, speedAvailability: SignalAvailability, gearAvailability: SignalAvailability, restrictionAvailability: SignalAvailability, lastUpdateMs: Long?)`. 초기값(`INITIAL`)은 `link=IDLE`, `restriction=UNKNOWN`, 나머지 null 과 `UNKNOWN`. 필드 이름과 의미는 `data-model.md` 의 "진단" 표 그대로.
- [X] T006 [P] `signal/src/test/.../car/FakeCarGateway.kt` 를 만든다. `MutableSharedFlow<CarEvent>` 를 감싸 `emit(event)` 를 제공하고, `events()` 를 수집하기 시작할 때 `openCount`, 수집이 끝날 때 `closeCount` 를 올린다. 테스트가 `Ready` 직후 초기값을 직접 `emit` 해 게이트웨이 동작을 흉내 낼 수 있어야 한다.
- [X] T007 [P] `signal/src/test/.../SwitchableSignalSourceTest.kt` 를 **먼저** 쓴다(실패 확인). 기존 `MockSignalSourceTest` 의 `runTest` 패턴을 따른다. 케이스: (a) 선택한 소스의 신호만 나온다 (b) `select(other)` 후에는 이전 소스가 신호를 더 내보내도 나오지 않고(두 소스를 번갈아 10회 바꿔도 섞이지 않음, SC-009) 이전 소스의 `stop()` 이 불린다 (c) 같은 소스를 다시 `select` 하면 아무 일도 없다 (d) `start()`/`stop()` 은 현재 소스에 위임한다 (e) `start()` 전에는 방출하지 않는다.
- [X] T008 `signal/.../SwitchableSignalSource.kt` 를 만들어 T007 을 통과시킨다. `VehicleSignalSource` 를 구현하고 `select(source: VehicleSignalSource)` 를 둔다. `signals` 는 선택된 소스에 대한 `flatMapLatest` 로 만들고, `select` 가 바뀌면 이전 소스를 `stop()` 한 뒤 새 소스를 선택한다. 소스가 실행 중이었다면 새 소스를 `start()` 하지는 않는다(시작은 호출하는 쪽의 책임, `contracts/car-signal-source.md` 4절).

**Checkpoint**: 게이트웨이 경계와 전환 소스 준비 완료. 이제 스토리를 시작할 수 있다.

---

## Phase 3: User Story 1 - 플랫폼의 주행 제한 상태에 따라 펫이 움직이거나 정적으로 바뀐다 (Priority: P1) 🎯 MVP

**Goal**: 플랫폼 제한 상태를 `Restriction` 신호로 엔진에 공급하고, 앱이 실차 모드(기본)로 열려 이를 따른다.

**Independent Test**: 에뮬레이터에서 앱을 열고 Virtual Console 에서 기어를 바꿔(P ↔ D) 펫 화면이 정적 ↔ 풀 모션으로 바뀌는지 본다. 데모 재생과 수동 토글은 쓰지 않는다(`quickstart.md` 시나리오 1, 2, 5).

### Tests for User Story 1 (먼저 쓰고 실패를 확인한다) ⚠️

- [X] T009 [P] [US1] `signal/src/test/.../car/CarSignalMapperTest.kt` 를 쓴다. 제한 매핑: `requiresDo=true, active=0` → `ACTIVE`, `requiresDo=false, active=0x50` → `ACTIVE`, `requiresDo=false, active=0` → `NONE`, `requiresDo=true, active=0x1ff` → `ACTIVE`. `Lost` → `Restriction(UNKNOWN)`. 중복 제거: 직전에 보낸 같은 제한 값은 다시 만들지 않고, 세션을 새로 시작하면 같은 값도 한 번 만든다. 시각: 시계 값이 `100, 90, 150` 으로 와도 결과 시각은 `100, 100, 150`(`max(직전 시각, clock() - 시작 시각)`, 감소 없음), 시작 시각이 0 이 되도록 첫 신호는 `clock() - start` 로 계산한다.
- [X] T010 [P] [US1] `signal/src/test/.../car/CarSignalSourceTest.kt` 를 쓴다(가짜 게이트웨이 사용, `runTest` 가상 시간). (a) `start()` 전에는 아무것도 내지 않는다 (b) `Ready` 뒤 `RestrictionChanged(true, 0x50)` → `Restriction(ACTIVE)` 하나 (c) 같은 값 반복은 한 번만, 값이 바뀌면 새 신호 (d) 제한 값이 오기 전에는 `Restriction` 을 내지 않고(엔진 기본값 `UNKNOWN` 사용) (e) `Lost` → `Restriction(UNKNOWN)`, 이어서 `Ready` 와 새 `RestrictionChanged` → 복구 값 (f) `tickIntervalMs=250` 마다 `Tick(t)` 가 나오고 `Tick` 시각이 앞선 신호 시각보다 작아지지 않음 (g) `stop()` 하거나 수집을 취소하면 `FakeCarGateway.closeCount` 가 올라가고 이후 이벤트는 나오지 않는다 (h) 두 번째 `start()` 는 게이트웨이를 새로 연다(`openCount` 2).
- [X] T011 [P] [US1] `app/src/test/.../ui/demo/DemoViewModelTest.kt` 에 케이스를 **먼저** 더한다(기존 케이스는 그대로 통과해야 한다). 기본 모드는 `VEHICLE`. `VEHICLE` 모드에서 `setRestriction(...)` 과 `togglePlay()` 는 엔진과 `DemoState` 에 아무 영향도 주지 않는다(FR-016). `DemoViewModel` 은 `vehicleSource: VehicleSignalSource` 를 생성자 인자로 받아 가짜 게이트웨이 기반 `CarSignalSource` 를 넣을 수 있어야 한다.

### Implementation for User Story 1

- [X] T012 [US1] `signal/.../car/CarSignalMapper.kt` 를 만들어 T009 를 통과시킨다. 세션마다 새로 만드는 클래스(`class CarSignalMapper(clock: () -> Long, startMs: Long)`)로, `restriction(requiresDo, active): Restriction?`(직전과 같으면 null), `lost(): Restriction`, `now(): Long`(시각 비감소)을 둔다. 규칙은 "`requiresDo || active != 0` 이면 `ACTIVE`, 아니면 `NONE`". 속도와 기어는 이 클래스가 제한에 쓰지 않는다(FR-004). Android API 를 쓰지 않는다.
- [X] T013 [US1] `signal/.../car/CarSignalSource.kt` 를 만들어 T010 을 통과시킨다. `VehicleSignalSource` 를 구현하고 생성자에 `gateway: CarGateway`, `clock: () -> Long`, `config: CarSignalConfig`, `log: (String) -> Unit = {}` 을 받는다. `MockSignalSource` 처럼 `running` 상태 + `transformLatest` 로 `signals` 를 만들고, 흐름이 수집되는 동안에만 `gateway.events()` 를 수집한다(`awaitClose` 에서 정리되는지는 게이트웨이 책임). 한 `Mutex`(또는 단일 채널) 안에서 `CarSignalMapper.now()` 로 시각을 붙이고 순서대로 `send` 한다. `tickIntervalMs` 마다 `Tick` 을 같은 경로로 보낸다(0 이하면 끔). `diagnostics: StateFlow<SignalDiagnostics>` 에 `link`(`CONNECTING` → `Ready` 에서 `CONNECTED` → `Lost` 에서 `LOST` → 정지 시 `IDLE`), `restriction`, `activeRestrictions`, `lastUpdateMs` 를 반영한다.
- [X] T014 [US1] `signal/.../car/AndroidCarGateway.kt` 를 제한 상태만 구현한다(`internal class AndroidCarGateway(context: Context) : CarGateway`). `events()` 는 `callbackFlow` 로, 수집 시작 시 `Car.createCar(context, handler, timeoutMs, CarServiceLifecycleListener)` 를 부른다. `onLifecycleChanged(car, ready)`: `ready=true` 면 `Ready` 를 보내고 `car.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager` 로 `getCurrentCarUxRestrictions()` 를 `RestrictionChanged(isRequiresDistractionOptimization(), getActiveRestrictions())` 로 한 번 보낸 뒤 `registerListener` 한다. `ready=false` 면 `Lost` 를 보낸다. `awaitClose` 에서 `unregisterListener()`(인자 없음, 스텁에서 확인)와 `car.disconnect()` 를 한다. `Car.createCar` 가 null 이거나 연결되지 않으면 `AvailabilityChanged(RESTRICTION, ERROR)` 를 보낸다. 해제한 `Car` 인스턴스는 다시 쓰지 않는다. T001 의 임시 빈 클래스를 이 구현으로 바꾼다.
- [X] T015 [US1] `app/.../ui/demo/DemoViewModel.kt` 를 고친다(T011 통과). `enum class SignalMode { VEHICLE, DEMO }` 와 `DemoState.mode`(기본 `VEHICLE`)를 추가하고, 엔진 소스를 `SwitchableSignalSource` 로 감싸 `vehicleSource` 와 기존 `PassiveSource(mock)` 중 모드에 맞는 것을 선택하게 한다. `VEHICLE` 모드에서 `togglePlay()` 와 `setRestriction()` 은 즉시 반환한다. `DEMO` 모드의 동작은 기존과 똑같다. 생성자에 `vehicleSource` 를 추가하되 기본값은 두지 않는다. 이 단계에서는 엔진을 `init` 에서 시작해 둔다(생명주기 연동은 US5).
- [X] T016 [US1] `app/.../ui/screens/HomeScreen.kt` 의 `DemoViewModel` 팩토리에서 `CarSignalSource(AndroidCarGateway(context.applicationContext), clock = SystemClock::elapsedRealtime, config = CarSignalConfig(), log = { Log.i("CarSignal", it) })` 를 만들어 넘긴다. `AndroidCarGateway` 가 `internal` 이면 `:app` 에서 쓸 수 있게 공개 팩토리(`CarSignalSource.create(context)`)를 `signal/.../car/CarSignalSource.kt` 에 둔다(모듈 경계: `:app` 이 `android.car` 를 직접 보지 않음).
- [X] T017 [US1] 에뮬레이터에서 `quickstart.md` 2절(설치와 첫 실행)과 3절 시나리오 1, 2(제한 부분), 5 를 확인하고 `verification-log.md` 에 결과를 적는다. 함께 R9-3(제한 상태 읽기에 권한이 필요한가)과 R9-6(`useLibrary`) 을 확정해 기록한다. 제한 읽기에 권한이 필요하다고 드러나면 T002 의 manifest 를 고치지 말고 **사용자에게 알린 뒤** 진행한다(헌장 IV).

**Checkpoint**: US1 완료 시 앱이 플랫폼 제한 상태만으로 정적 ↔ 풀 모션을 오간다(MVP).

---

## Phase 4: User Story 2 - 차량의 속도와 기어 변화가 엔진 신호로 전달된다 (Priority: P1)

**Goal**: 속도(m/s)와 기어(주차 여부)를 `Speed`, `ParkedState` 신호로 전달한다.

**Independent Test**: Virtual Console 에서 기어를 P, D, R, N 으로 바꿔 진단의 주차 여부가 P 일 때만 참인지, 속도가 바뀌면 진단 속도가 따라가는지 본다(시나리오 2, 3, 4).

### Tests for User Story 2 ⚠️

- [X] T018 [P] [US2] `signal/src/test/.../car/CarSignalMapperTest.kt` 에 속도와 기어를 더한다. 속도: `mps` 가 유한하고 0 이상이면 `Speed(mps)`, `0` 도 통과, 0 근처 미세한 음수(`-ε`, ε = 1e-3 m/s `[확인 필요]`)는 0 으로 보정, 그 밖의 음수와 NaN, `±Infinity` 는 버린다(null). 기어: `gear == 0`(GEAR_UNKNOWN)은 null, `4`(GEAR_PARK)는 `ParkedState(parked=true)`, `8`(DRIVE), `2`(REVERSE), `1`(NEUTRAL), 그 밖의 0 이 아닌 값은 `parked=false`. 같은 종류의 직전 값과 같으면 null(중복 제거), 세션을 새로 시작하면 같은 값도 한 번 만든다.
- [X] T019 [P] [US2] `signal/src/test/.../car/CarSignalSourceTest.kt` 에 케이스를 더한다. (a) `Ready` 직후 게이트웨이가 낸 초기 `SpeedChanged`, `GearChanged`, `RestrictionChanged` 가 각각 정확히 한 번씩 신호가 된다(FR-007) (b) 같은 시각에 속도, 기어, 제한이 함께 오면 출력 순서가 `Speed`, `ParkedState`, `Restriction`, `Tick` 이다 (c) 콜백이 뒤섞인 시각으로 와도 출력 `timestampMs` 가 감소하지 않는다(FR-008) (d) 서로 다른 스레드에서 `emit` 해도 출력이 유실되거나 순서가 깨지지 않는다(`Dispatchers.Default` 여러 코루틴으로).

### Implementation for User Story 2

- [X] T020 [US2] `signal/.../car/CarSignalMapper.kt` 에 `speed(mps): Speed?` 와 `gear(gear): ParkedState?` 를 추가해 T018 을 통과시킨다. 속도 보정 상수(`SPEED_EPSILON_MPS = 1e-3f`)는 주석으로 `[확인 필요]` 를 남긴다. `Speed` 의 `require` 에 걸리기 전에 걸러낸다.
- [X] T021 [US2] `signal/.../car/CarSignalSource.kt` 가 `SpeedChanged`, `GearChanged` 를 매퍼로 변환해 내보내게 하고(T019 통과), 같은 시각의 출력 순서를 속도, 주차, 제한, `Tick` 으로 고정한다. 진단에 `speedMps`, `parked` 를 반영한다.
- [X] T022 [US2] `signal/.../car/AndroidCarGateway.kt` 에 속도와 기어를 더한다. `car.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager` 로, 각 속성은 `getCarPropertyConfig(id) != null` 로 지원 여부를 판단한다(차종명, 지역 분기 금지). 지원하지 않으면 `AvailabilityChanged(signal, UNSUPPORTED)`. 지원하면 `Ready` 직후 현재 값을 읽어 `GearChanged(getIntProperty(VehiclePropertyIds.GEAR_SELECTION, 0))` 와 `SpeedChanged(getProperty(Float::class.java, VehiclePropertyIds.PERF_VEHICLE_SPEED, 0).value)` 로 한 번씩 보내고 `AvailabilityChanged(.., OK)` 를 보낸다. 변화 구독은 `registerCallback(callback, PERF_VEHICLE_SPEED, config.speedRate)` 와 `registerCallback(callback, GEAR_SELECTION, SENSOR_RATE_ONCHANGE)`. `awaitClose` 에서 `unregisterCallback(callback)` 를 반드시 한다. 속성 ID 와 `VehicleGear` 값은 스텁에서 확인한 값(`PERF_VEHICLE_SPEED`=291504647, `GEAR_SELECTION`=289408000, PARK=4)을 쓰되 상수는 `VehiclePropertyIds`, `VehicleGear` 를 참조한다.
- [X] T023 [US2] 에뮬레이터에서 `quickstart.md` 시나리오 2(기어 부분), 3, 4 를 확인하고 `verification-log.md` 에 적는다. SC-003 은 P, D, R, N 4가지 모두 기대값이 나와야 한다. Virtual Console 에 속도 카드가 있는지(R8 `[확인 필요]`)도 기록한다. 속도 카드가 없으면 시나리오 4 는 "확인 불가"로 적고 사유를 남긴다.

**Checkpoint**: US2 완료 시 엔진이 속도와 주차 상태까지 실차에서 받는다.

---

## Phase 5: User Story 3 - 신호를 받을 수 없을 때 안전하게 정적으로 떨어진다 (Priority: P1)

**Goal**: 권한 없음, 서비스 없음, 미지원, 연결 끊김에서도 앱이 죽지 않고 정적을 유지한다.

**Independent Test**: `CAR_SPEED` 를 회수하고 열기, 신호 미지원/서비스 없음 상황에서 열기로 앱이 종료되지 않고 펫이 정적이며 이유가 진단에 나오는지 본다(시나리오 6, 7).

### Tests for User Story 3 ⚠️

- [X] T024 [US3] `signal/src/test/.../car/CarSignalSourceTest.kt` 에 케이스를 더한다. (a) 게이트웨이가 `AvailabilityChanged(RESTRICTION, ERROR)` 만 내고 값을 못 주면 예외 없이 계속 열려 있고 `Restriction` 을 내지 않는다(엔진 기본 `UNKNOWN`) (b) `AvailabilityChanged(SPEED, NO_PERMISSION)` 이어도 기어와 제한 신호는 정상 전달된다(FR-010) (c) `AvailabilityChanged(SPEED, UNSUPPORTED)` 도 같다 (d) 진단의 `speedAvailability`, `gearAvailability`, `restrictionAvailability` 가 이벤트대로 바뀐다 (e) 연결 상태가 `CONNECTED` 인 채 가상 시간으로 10분이 지나도 `Restriction(UNKNOWN)` 이 새로 나오지 않고 마지막 값이 유지되며 `lastUpdateMs` 는 그대로이고 `link=CONNECTED` 이다(스펙 US3-4) (f) `Lost` 후 `Ready` 에서 초기값이 다시 나온다(FR-012) (g) 게이트웨이 흐름이 예외로 끝나면 소스가 예외를 밖으로 던지지 않고 `link=UNAVAILABLE`, 제한은 `UNKNOWN` 으로 남는다.
- [X] T025 [P] [US3] `app/src/test/.../ui/demo/DemoViewModelTest.kt` 에 케이스를 더한다. `onSpeedPermissionResult(granted)` 가 `VEHICLE` 모드에서 수신 소스를 한 번 재시작한다(속도 포함 여부는 게이트웨이가 판단하므로 `stop()` 후 `start()` 호출 횟수만 확인). `DEMO` 모드에서는 아무 일도 하지 않는다.

### Implementation for User Story 3

- [X] T026 [US3] `signal/.../car/CarSignalSource.kt` 에서 T024 를 통과시킨다. 게이트웨이 흐름의 예외를 잡아 `link=UNAVAILABLE` 로 만들고 소스를 살려 둔다(`catch`). `AvailabilityChanged` 를 진단에 반영한다. 시간이 지나도 값을 `UNKNOWN` 으로 바꾸지 않는다. `Lost` 에서는 `Restriction(UNKNOWN)` 을 한 번만 내보낸다.
- [X] T027 [US3] `signal/.../car/AndroidCarGateway.kt` 에 안전망을 더한다. 속도 읽기 전에 `context.checkSelfPermission(Car.PERMISSION_SPEED) == PERMISSION_GRANTED` 를 확인해 거부면 `AvailabilityChanged(SPEED, NO_PERMISSION)` 를 보내고 속도 읽기와 구독을 건너뛴다. 읽기와 구독 호출은 `IllegalArgumentException`, `SecurityException`, 그 밖의 `Exception` 을 잡아 해당 신호만 `ERROR`(권한 예외는 `NO_PERMISSION`)로 표시하고 계속한다. 콜백 안에서 예외가 새지 않게 한다. 기어와 제한은 속도와 독립이다.
- [X] T028 [US3] `app/.../ui/demo/DemoViewModel.kt` 에 `onSpeedPermissionResult(granted: Boolean)` 를 더한다(T025 통과). `VEHICLE` 모드에서 수신 중이면 `engine.stop()` → `engine.start()` 로 재시작한다. `app/.../MainActivity.kt` 에서 `ActivityResultContracts.RequestPermission()` 으로 `android.car.permission.CAR_SPEED` 를 앱 시작 시 한 번 요청하고(이미 허용이면 건너뜀) 결과를 `onSpeedPermissionResult` 로 넘긴다. 요청이 시스템에서 거절되거나 다이얼로그가 안 떠도 앱은 계속 동작해야 한다(R9-2 `[확인 필요]`).
- [X] T029 [US3] 에뮬레이터에서 `quickstart.md` 시나리오 6(권한 회수 후 재시작), 7(서비스 끊김, 환경에서 불가하면 건너뛰고 T024 의 가짜 게이트웨이 테스트로 대체) 을 확인하고 `verification-log.md` 에 적는다. R9-2(`CAR_SPEED` 부여 방식: 다이얼로그인지 `pm grant` 가 필요한지)를 확정해 적는다.

**Checkpoint**: US3 완료 시 실패 상황에서도 안전하게 정적으로 떨어진다.

---

## Phase 6: User Story 4 - 실차 신호 모드와 데모 모드를 오가도 서로 섞이지 않는다 (Priority: P2)

**Goal**: 앱 안에서 실차와 데모를 전환하고, 진단 정보를 개발자가 볼 수 있다.

**Independent Test**: 실차 → 데모 → 실차로 전환하며 데모를 재생했다가 돌아와 데모 신호가 남지 않는지 본다(시나리오 11, 12).

### Tests for User Story 4 ⚠️

- [X] T030 [US4] `app/src/test/.../ui/demo/DemoViewModelTest.kt` 에 케이스를 더한다. (a) `setMode(DEMO)` → 엔진이 새 세션으로 시작되고 `togglePlay()`, `setRestriction()` 이 기존대로 동작한다 (b) 재생 중 `setMode(VEHICLE)` → `playback=STOPPED`, `elapsedMs=0`, `manualRestriction=null`, 목업 소스 정지, 이후 목업 신호가 엔진에 안 들어간다 (c) `VEHICLE` ↔ `DEMO` 를 10회 번갈아 전환해도 출력에 이전 모드 신호가 섞이지 않는다(SC-009, 가짜 소스로 구분 가능한 신호를 넣는다) (d) `DemoState.diagnostics` 는 `VEHICLE` 모드에서만 값이 있고 `DEMO` 에서는 null (e) 같은 모드를 다시 설정하면 아무 일도 없다.

### Implementation for User Story 4

- [X] T031 [US4] `app/.../ui/demo/DemoViewModel.kt` 에 `setMode(mode: SignalMode)` 와 `DemoState.diagnostics: SignalDiagnostics?` 를 구현한다(T030 통과). 순서는 `contracts/car-signal-source.md` 5절 그대로: `engine.stop()` → `SwitchableSignalSource.select` → `engine.start()`. `VEHICLE` 로 갈 때 재생과 수동 제한을 초기화한다. `vehicleSource` 가 진단을 내는 경우(`CarSignalSource`)에만 `diagnostics` 를 `DemoState` 로 합쳐 낸다(타입 확인 후 캐스팅하지 말고 `diagnostics: StateFlow<SignalDiagnostics>?` 를 `VehicleSignalSource` 를 감싼 작은 인터페이스 `DiagnosticSource` 로 받는다).
- [X] T032 [US4] `app/.../ui/demo/DemoControls.kt` 를 고친다. 맨 위에 모드 선택(`실차`/`데모` 두 칩, 기존 버튼 스타일 유지)을 둔다. `VEHICLE` 모드에서는 재생 버튼, 시나리오 시간, 수동 제한 3칩을 숨기고, 진단 섹션을 보여준다: 모드, 연결 상태, 제한 상태, 마지막 제한 비트(`0x%x`), 속도(m/s 와 km/h), 기어(주차/주차 아님), 마지막 갱신 시각, 신호별 이유(`권한 거부`, `미지원`, `오류`, `확인 중`). 글자는 12sp 이상(AGENTS.md 5절). `@Preview` 를 모드별로 하나씩 더한다. `debugAvailable` 이 거짓이면 아무것도 그리지 않는 기존 규칙은 유지한다.
- [X] T033 [US4] `app/.../ui/screens/HomeScreen.kt` 에서 `DemoControls` 에 `onMode = viewModel::setMode` 를 연결하고, `signal/.../car/CarSignalSource.kt` 의 `log` 로 `CarSignal` 태그에 모드 전환, 연결 상태 변화, 제한 상태 변화를 한 줄씩 남긴다(속도처럼 자주 오는 값은 남기지 않는다, 헌장 V). `DemoViewModel.setMode` 도 전환 시 한 줄 남긴다.
- [X] T034 [US4] 에뮬레이터에서 `quickstart.md` 시나리오 11, 12 를 확인하고 `verification-log.md` 에 적는다. 모드를 10회 번갈아 전환하며 `logcat -s CarSignal` 로 전환 순서가 `stop → select → start` 인지 본다.

**Checkpoint**: US4 완료 시 영상 촬영(데모)과 실차 확인(실차)을 한 앱에서 오갈 수 있다.

---

## Phase 7: User Story 5 - 앱이 쓰이지 않을 때 신호 수신을 멈추고 자원을 돌려준다 (Priority: P2)

**Goal**: 화면에 보일 때만 수신하고, 사라지면 정리한다.

**Independent Test**: 열고, 홈으로 보내고, 다시 열어 보이지 않는 동안 수신이 없고 돌아오면 현재 값이 한 번 전달되는지 본다. 20회 반복해 연결이 누적되지 않는지 본다(시나리오 8, 9, 10).

### Tests for User Story 5 ⚠️

- [X] T035 [US5] `app/src/test/.../ui/demo/DemoViewModelTest.kt` 에 케이스를 더한다. (a) `VEHICLE` 모드에서 `onForeground()` → 수신 시작(가짜 게이트웨이 `openCount` 1), 이미 수신 중에 `onForeground()` 를 또 불러도 늘지 않는다(멱등, 회전) (b) `onBackground()` → 수신 정리(`closeCount` 가 `openCount` 와 같아지고 진단 `link=IDLE`) (c) `onBackground()` 뒤 `onForeground()` → 새로 열려 `openCount` 2, 초기값이 다시 한 번씩 나온다(FR-013, 새 연결) (d) 20회 반복해도 `openCount == closeCount`(SC-007) (e) `DEMO` 모드에서는 `onForeground()`/`onBackground()` 가 데모 재생과 엔진을 건드리지 않는다.

### Implementation for User Story 5

- [X] T036 [US5] `app/.../ui/demo/DemoViewModel.kt` 에 `onForeground()`, `onBackground()` 를 구현한다(T035 통과). `VEHICLE` 모드에서만 `engine.start()` / `engine.stop()` 을 부르고 중복 호출은 무시한다. T015 의 `init { engine.start() }` 를 `DEMO` 모드(기본이 아님)와 실차 모드를 구분하도록 바꾼다: 실차 모드의 엔진은 포그라운드 동안만 돌고, 데모 모드의 엔진은 기존처럼 항상 켜 둔다(수동 제한 주입이 항상 처리되도록). `onCleared()` 는 소스와 엔진을 정리한다(기존 유지).
- [X] T037 [US5] `app/.../ui/screens/HomeScreen.kt`(또는 `MainActivity.kt`)에서 `LifecycleStartEffect`/`LifecycleEventEffect(ON_START, ON_STOP)` 로 `viewModel.onForeground()`, `onBackground()` 를 연결한다. 화면 회전으로 Activity 가 다시 만들어져도 중복 시작이 되지 않는지(T035 의 멱등)를 `adb` 로 확인한다. CCP Back 키가 `moveTaskToBack(true)` 로 숨길 때 `onStop` 이 오면 수신이 멈추는지 본다.
- [X] T038 [US5] 에뮬레이터에서 `quickstart.md` 시나리오 8, 9, 10 을 확인하고 `verification-log.md` 에 적는다. 시나리오 10 은 열고 닫기를 20회 반복하며 `$ADB shell dumpsys car_service` 에서 클라이언트 수가 늘지 않는지 비교한다(전후 값을 적는다).

**Checkpoint**: US5 완료 시 모든 스토리가 동작한다.

---

## Phase 8: Polish & Cross-Cutting

**Purpose**: 문서와 계획의 어긋남을 맞추고 전체를 한 번 점검한다

- [X] T039 `./gradlew :signal:testDebugUnitTest :engine:testDebugUnitTest :pet:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug --console=plain` 으로 전체 테스트와 빌드를 돌려 모두 통과하는지 확인한다. 기존 001, 002 테스트가 그대로 통과해야 한다(SC-008). 실패하면 원인을 고치고 결과를 `verification-log.md` 에 적는다.
- [X] T040 `quickstart.md` 4절(성능과 안전)을 확인한다. 주행 제한이 걸린 동안 CPU 5% 미만(`adb shell top -H -p $(pidof com.example.dashpet)`), `Tick` 250 ms 가 CPU 를 늘리지 않는지, 정적 등급에서 프레임 루프가 돌지 않는지. 기준을 못 맞추면 `CarSignalConfig.tickIntervalMs` 를 조정하고 근거를 적는다. 결과를 `verification-log.md` 에 적는다.
- [X] T041 문서를 구현에 맞춘다. `contracts/car-signal-source.md` 2절과 3절, `data-model.md` 의 `CarGateway`, `CarEvent`(`AvailabilityChanged`, `CarSignal` 추가) 설명을 "현재 값 읽기는 게이트웨이가 `Ready` 직후 이벤트로 내보낸다" 로 고치고, `DiagnosticSource` 를 `data-model.md` 와 `contracts` 5절에 반영한다. `research.md` R9 의 6건에 확인 결과(해결/미결)를 적는다. 구현 중 바뀐 점이 `plan.md` 와 어긋나면 `plan.md` 도 고친다.
- [X] T042 `AGENTS.md` 12절의 새 기능 체크를 항목별로 점검해 `verification-log.md` 마지막에 표로 적는다: 권한 최소화(두 개), Capability 확인(속성 설정 존재), 주행 규제 대응(제한 상태 따름), 오디오 포커스(해당 없음), 가로/세로 대응(회전 시 수신 유지), CCP Back(기존 유지), `release` 시점(수집 종료 시 해제), 비밀값 비노출(없음). 대회 측 확인이 필요한 표준 API 사용 허용 여부와 `android.car.permission.*` 선언은 **사용자에게 알려야 할 미결 사항**으로 맨 위에 적는다.

---

## Dependencies & Execution Order

### Phase 의존

- **Phase 1 (Setup)**: 바로 시작. T001 이 끝나야 `:signal` 에서 `android.car` 를 참조하는 모든 작업이 가능하다. T002, T003 은 T001 과 병렬.
- **Phase 2 (Foundational)**: Phase 1 의 T001 이 끝난 뒤. 모든 스토리를 막는다. T004 → T005, T006, T007 병렬 → T008.
- **Phase 3~7 (스토리)**: Phase 2 완료 후. US1 → US2 → US3 순서가 가장 자연스럽다(같은 `CarSignalSource.kt`, `AndroidCarGateway.kt`, `CarSignalMapper.kt` 를 이어서 고친다). US4, US5 는 US1 의 `DemoViewModel` 변경(T015)에 의존한다.
- **Phase 8 (Polish)**: 모든 스토리 완료 후.

### 스토리 의존

- **US1 (P1)**: Phase 2 만. MVP.
- **US2 (P1)**: US1 의 `CarSignalSource`, `AndroidCarGateway`, `CarSignalMapper` 위에 더한다. 테스트는 독립적으로 쓸 수 있다.
- **US3 (P1)**: US1, US2 의 파일 위에 더한다(실패 경로). 가짜 게이트웨이 테스트로 독립 검증 가능.
- **US4 (P2)**: US1 의 `SignalMode`, `SwitchableSignalSource`(T015) 위에 더한다.
- **US5 (P2)**: US1 의 `DemoViewModel`(T015) 위에 더한다. US4 와 같은 파일을 고치므로 US4 뒤에 한다.

### 스토리 안의 순서

- 테스트 먼저(실패 확인) → 변환(`CarSignalMapper`) → 소스(`CarSignalSource`) → 게이트웨이(`AndroidCarGateway`) → 앱 연결 → 에뮬레이터 확인.
- 같은 파일을 고치는 작업(`CarSignalSource.kt`, `AndroidCarGateway.kt`, `CarSignalMapper.kt`, `DemoViewModel.kt`, `DemoViewModelTest.kt`)은 병렬로 하지 않는다.

## Parallel Opportunities

- **Phase 1**: T002, T003 병렬(T001 과도 병렬 가능, 다른 파일).
- **Phase 2**: T005, T006, T007 병렬(서로 다른 파일, T004 후).
- **US1 테스트**: T009, T010, T011 병렬(서로 다른 파일).
- **US2 테스트**: T018 과 T019 는 서로 다른 파일이라 병렬. 구현 T020 → T021 → T022 는 순서대로.
- **US3**: T024(소스 테스트)와 T025(ViewModel 테스트)는 병렬.
- **Polish**: T039 와 T041 은 병렬 가능. T040 은 에뮬레이터가 필요해 T039 뒤.

```text
예시 (US1 테스트를 한꺼번에):
  Task: "T009 CarSignalMapperTest 제한 매핑, 시각, 중복 제거"
  Task: "T010 CarSignalSourceTest 수명, 제한, Tick, 해제"
  Task: "T011 DemoViewModelTest 기본 모드와 실차 모드 무시 규칙"
```

## Implementation Strategy

### MVP (US1 만)

1. Phase 1, Phase 2 를 끝낸다.
2. US1 을 끝낸다(T009~T017).
3. **멈추고 검증**: 에뮬레이터에서 기어를 바꿔 정적 ↔ 풀 모션이 실차 제한 상태를 따르는지 확인한다.
4. 여기서 R9-3(제한 읽기 권한), R9-6(`useLibrary`)이 풀리고, 표준 API 사용 허용 여부는 사용자에게 알린다.

### 점진적 전달

1. US1 → 제한 상태 연동(안전의 핵심) 확인.
2. US2 → 속도와 기어까지.
3. US3 → 실패 상황 대응.
4. US4 → 데모와 오가기. 11/15 영상 촬영에 쓰는 데모를 그대로 유지한다.
5. US5 → 생명주기 정리.

## Notes

- `[P]` 작업은 서로 다른 파일일 때만 붙였다.
- 테스트는 구현 전에 쓰고 실패를 확인한다(헌장 III).
- 에뮬레이터 확인 결과는 `verification-log.md` 에 남긴다. 확인하지 못한 항목은 비워 두지 말고 "확인 불가"와 사유를 적는다(헌장 VI).
- 커밋은 사용자가 요청할 때만 한다.
- 새 권한, 새 의존성은 이 목록 밖에서 추가하지 않는다. 필요하면 사용자에게 먼저 알린다.
