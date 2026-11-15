# Implementation Plan: 실차 신호 연결

**Branch**: `003-real-vehicle-signal` | **Date**: 2026-11-10 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/003-real-vehicle-signal/spec.md`

## Summary

엔진이 쓰는 `VehicleSignalSource` 의 실차 구현체 `CarSignalSource` 를 `:signal` 에 만든다. AAOS 표준 API 로 속도(`PERF_VEHICLE_SPEED`),
기어(`GEAR_SELECTION`), 주행 제한(`CarUxRestrictionsManager`)을 읽어 기존 신호(`Speed`, `ParkedState`, `Restriction`, `Tick`)로 바꿔 내보낸다.
엔진 이후(`:engine`, `:pet`)는 바꾸지 않는다.

접근 방식:
- 차량 서비스 접근(`AndroidCarGateway`, `android.car` 를 만지는 유일한 파일)과 변환 규칙(`CarSignalMapper`, 순수 함수)을 분리한다.
  변환, 연결 수명, 중복 제거, 시각 보장은 가짜 게이트웨이로 JVM 단위 테스트한다(헌장 III).
- 연결 수명을 신호 흐름의 수집 수명에 묶는다. 수집이 시작되면 `Car` 를 만들고, 끝나면 리스너 해제와 `disconnect()` 를 한다. 백그라운드에서는 수집을 멈춘다.
- 제한 상태는 `requiresDistractionOptimization || activeRestrictions != 0` 이면 `ACTIVE`. 읽지 못하거나 끊기면 `UNKNOWN`(정적). 속도와 기어는 제한 판단에 쓰지 않는다.
- `:app` 에는 `SignalMode`(실차/데모)와 `SwitchableSignalSource` 로 모드를 오가게 하고, 진단 패널에 연결 상태와 신호별 이유를 보여준다. 기존 데모는 그대로 둔다.
- 구현에서 바뀌거나 추가된 것(2026-11-10): 현재 값 읽기는 게이트웨이가 `Ready` 직후 이벤트로 내보낸다. 진단은 `StateFlow` 인자로 `DemoViewModel` 에 전달한다(`DiagnosticSource` 인터페이스 없음).
  권한 확인이 속성 설정 확인보다 먼저다(권한 없으면 설정이 숨겨짐). `MainActivity` 에 `configChanges` 를 선언했다(재생성 시 수신이 끊기던 문제). 진단은 값이 실제로 바뀔 때만 갱신한다(정적 상태 CPU 16% → 1%).
  게이트웨이 실패 때 제한을 `UNKNOWN` 으로 되돌린다. 자세한 내용과 측정은 [checklists/verification-log.md](./checklists/verification-log.md), [research.md](./research.md) R9.
- 사전 조사로 이미 확인한 것: `android.car.jar` 스텁이 SDK 에 있고 런타임에는 부트 클래스패스로 제공됨, `CAR_SPEED` 는 dangerous 권한,
  이 에뮬레이터에는 `hmg_car_service` 가 없어 `driving_regulation` 명령을 쓸 수 없음(상세는 [research.md](./research.md)).

## Technical Context

**Language/Version**: Kotlin 2.2.10, JVM target 11 (기존)

**Primary Dependencies**: 새 외부 라이브러리 없음. `:signal` 에서 `android.car` 를 컴파일 전용으로 참조(`useLibrary("android.car")`, 안 되면
`compileOnly(files(.../platforms/android-34/optional/android.car.jar))`). kotlinx-coroutines 1.10.2(기존). Pleos SDK 는 이번에 쓰지 않는다(표준 API 가 불허될 때의 대체 경로로만 남김).

**Storage**: N/A. 진단 값은 메모리에만 둔다.

**Testing**: JUnit4 + kotlinx-coroutines-test. 매퍼, 소스, 전환 소스는 가짜 게이트웨이로 JVM 단위 테스트. `AndroidCarGateway` 는 `android.car` 를 직접 쓰므로
JVM 에서 검증할 수 없고, 에뮬레이터에서 [quickstart.md](./quickstart.md) 로 검증한다(헌장 III 마지막 항목).

**Target Platform**: Android Automotive(Pleos Connect). 에뮬레이터 `Pleos_Connect_v2.1.0`(API 34). compileSdk 34, minSdk 29(기존 모듈 설정 유지).

**Project Type**: 기존 멀티 모듈 Android 앱. `:signal` 에 신호 구현을 더하고 `:app` 에 모드와 진단 UI 를 더한다.

**Performance Goals**: 제한 상태 변화가 화면에 반영되기까지 1초 이내(SC-001). 신호 수신 중 주행 제한 상태 CPU 5% 미만(AGENTS.md 5절). 속도는 UI 주기(약 수 Hz)로 구독.

**Constraints**: 제한 판단은 플랫폼 제한 상태만 근거(헌장 I). 지역, 차종명 분기 금지. `android.car` 는 `AndroidCarGateway` 밖으로 새지 않는다. 화면 갱신은 메인 스레드.
수신은 화면에 보일 때만. 권한은 두 개로 한정.

**Scale/Scope**: 신규 파일 약 7개(`:signal` 6, 테스트 별도), 수정 파일 약 6개(`:app`, `:signal` 빌드, manifest). 신호 3종, 모드 2종.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 원칙 | 점검 | 결과 |
|-|-|-|
| I. 주행 안전 우선 | 제한 상태는 플랫폼 값(`CarUxRestrictions`)이 1순위이고 속도, 기어는 입력으로 쓰지 않는다. 모르면 `UNKNOWN`(정적): 시작 직후, 권한 없음, 서비스 없음, 연결 끊김 모두 해당. 지역 분기 없음. 모션 등급 판단은 여전히 엔진(`MotionGate`) 한 곳 | PASS |
| II. 신호 소스 추상화와 모듈 경계 | `CarSignalSource` 는 `VehicleSignalSource` 를 그대로 구현한다. `android.car` 호출은 `:signal` 의 `AndroidCarGateway` 에만 둔다. 의존 방향(app → pet → engine → signal) 유지. `:engine`, `:pet` 변경 없음. 헌장은 구현체 이름을 `PleosSignalSource` 로 예시했지만 이번에는 Pleos SDK 를 쓰지 않으므로 `CarSignalSource` 로 짓는다 | PASS (이름만 다름, 아래 기록) |
| III. 테스트 우선 | 매퍼, 소스, 전환 소스는 테스트를 먼저 쓴다(Red-Green). `AndroidCarGateway` 는 JVM 검증 불가라 에뮬레이터 확인과 기록으로 대체한다 | PASS (게이트웨이만 수동 근거) |
| IV. 플랫폼 접근과 권한 최소화 (구현 후: `CAR_SPEED` 가 위치 권한 그룹이라 다이얼로그가 "위치 정보"로 보임, 대회 측 확인 필요) | 하드웨어 직접 접근 없음, 표준 API 만. 지원 여부는 속성 설정 존재 여부로 판단. 권한은 `CAR_SPEED`, `CAR_POWERTRAIN` 두 개. SDK 수명주기(생성, 리스너, 해제, 재사용 금지)를 지킨다. 다만 `pleos.car.permission.*` 가 아닌 `android.car.permission.*` 를 선언한다 | CONDITIONAL PASS (아래 Complexity Tracking) |
| V. 비밀값과 개인정보 | 위치와 개인정보를 읽지 않는다. 속도, 기어, 제한 값은 저장하지 않고 로그에 자주 오는 값을 한 줄씩 남기지 않는다 | PASS |
| VI. 근거 없는 값 금지 | 상수는 스텁과 에뮬레이터에서 확인한 값만 쓴다(`PERF_VEHICLE_SPEED`, `GEAR_SELECTION`, `VehicleGear`, 권한 문자열). 미확인 6건은 [research.md](./research.md) R9 에 `[확인 필요]` 로 기록 | PASS |

**Re-check after Phase 1 design**: 설계 후에도 위 결과는 바뀌지 않았다. 새로 생긴 위험은 없고, 조건부 통과 1건(권한)만 남는다.

## Project Structure

### Documentation (this feature)

```text
specs/003-real-vehicle-signal/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   └── car-signal-source.md
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
signal/
├── build.gradle.kts                                   # 수정: android.car 컴파일 전용 참조
└── src/
    ├── main/java/com/example/dashpet/signal/
    │   ├── SwitchableSignalSource.kt                  # 신규: 현재 소스의 신호만 내보냄
    │   └── car/
    │       ├── CarSignalSource.kt                     # 신규: VehicleSignalSource 구현(연결 수명, Tick, 진단)
    │       ├── CarSignalMapper.kt                     # 신규: 순수 변환 규칙(속도, 기어, 제한, 시각)
    │       ├── CarGateway.kt                          # 신규: CarEvent, SignalAvailability, 게이트웨이 인터페이스, 설정
    │       ├── AndroidCarGateway.kt                   # 신규: android.car 어댑터(이 파일만 android.car 사용)
    │       └── SignalDiagnostics.kt                   # 신규: 진단 값 객체, CarLinkState
    └── test/java/com/example/dashpet/signal/
        ├── SwitchableSignalSourceTest.kt              # 신규
        └── car/
            ├── CarSignalMapperTest.kt                 # 신규
            ├── CarSignalSourceTest.kt                 # 신규
            └── FakeCarGateway.kt                      # 신규: 테스트용 가짜

app/
├── src/main/AndroidManifest.xml                       # 수정: 권한 2개, MainActivity configChanges
└── src/main/java/com/example/dashpet/
    ├── ui/demo/DemoViewModel.kt                       # 수정: SignalMode, onForeground/onBackground, 모드 전환, 진단 전달
    ├── ui/demo/DemoControls.kt                        # 수정: 모드 선택, 진단 표시, 실차 모드에서 재생/수동 제한 숨김
    ├── ui/demo/DiagnosticsText.kt                     # 신규: 진단을 화면에 보일 줄로 바꾸는 순수 함수
    └── ui/screens/HomeScreen.kt                       # 수정: 생명주기 효과, CAR_SPEED 런타임 요청, 소스 생성(권한 요청은 MainActivity 대신 여기서 한다)
app/src/test/java/com/example/dashpet/ui/demo/
├── DemoViewModelTest.kt                               # 보강: 모드 전환, 수동 제한 무시, 수명주기, 권한 결과
└── DiagnosticsTextTest.kt                             # 신규: 진단 줄 형식
```

**Structure Decision**: 기존 4개 모듈(`:app` → `:pet` → `:engine` → `:signal`)을 그대로 쓰고 새 모듈을 만들지 않는다. 차량 접근은 `:signal` 안의 `car` 패키지로 모은다.
`:engine`, `:pet` 은 바꾸지 않는다(스펙 FR-022, SC-008).

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| `android.car.permission.CAR_SPEED`, `CAR_POWERTRAIN` 선언(헌장 IV 는 `pleos.car.permission.*` 만 선언하라고 함) | 속도와 기어를 읽는 표준 API 의 권한이 AAOS 표준 문자열이고, 에뮬레이터 이미지에 `pleos.car.permission.CAR_SPEED` 가 없다(research R4). 문서도 `PERF_VEHICLE_SPEED` 의 권한 문자열을 명시하지 않는다 | pleos 권한만 선언하면 속도를 못 읽는다. Pleos Vehicle SDK 는 속도 API 가 없고 기어는 deprecated 라 대체가 안 된다. 허용 여부는 대회 측 확인 전이라 두 개로 한정하고 결과를 기록한다 |
| `SwitchableSignalSource` 추가(소스 래퍼 1개) | 엔진과 `PetController` 연결을 유지한 채 실차와 데모를 오가야 한다(research R7) | 모드마다 엔진을 새로 만들면 화면이 `collectFrom` 을 다시 걸어야 하고 회전 시 상태 보존이 복잡해진다 |
| 구현체 이름을 `CarSignalSource` 로 함(헌장 예시는 `PleosSignalSource`) | 이번 구현은 Pleos SDK 가 아니라 AAOS 표준 API 를 쓴다 | `PleosSignalSource` 로 지으면 실제 의존과 이름이 어긋난다. 이후 Pleos 기반 소스가 생기면 그 이름을 쓴다. 헌장 문구 개정은 필요하면 별도로 한다 |
