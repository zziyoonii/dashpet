# 수동 확인 기록 (실차 신호 연결)

## 사용자에게 알려야 할 미결 사항 (대회 측 확인 필요)

1. **표준 API 와 `android.car.permission.*` 선언을 허용하는가.** 이 기능은 속도(`PERF_VEHICLE_SPEED`), 기어(`GEAR_SELECTION`), 주행 제한(`CarUxRestrictionsManager`)을 AAOS 표준 API 로 읽는다. 헌장 IV 는 `pleos.car.permission.*` 만 선언하라고 하는데, 이 이미지에는 `pleos.car.permission.CAR_SPEED` 가 없고 Pleos Vehicle SDK 에는 속도 API 가 없다. 불허되면 같은 인터페이스 뒤에서 SDK 로 바꿀 수 있지만 속도는 읽을 수 없다.
2. **`CAR_SPEED` 는 위치 권한 그룹이다.** 사용자에게 "이 기기의 위치 정보에 액세스하도록 허용하시겠습니까?" 로 보인다. 앱은 위치를 읽지 않는다. 설명 문구나 심사 대응이 필요하다(헌장 V). 이 앱의 핵심(제한 상태, 기어)은 이 권한 없이 동작하므로 속도만 포기하면 권한 요청을 없앨 수 있다.
3. **풀 모션 CPU 가 에뮬레이터에서 기준(정차 25% 이하)을 넘는다**(35~64%). 003 이 아니라 002 의 렌더링 비용이며 실차에서 다시 재야 한다.
4. 가속 이벤트 품질(속도 5 Hz 구독이 엔진 가정에 맞는지)은 가속 장면이 있는 실차 주행에서 확인해야 한다.

에뮬레이터가 필요한 항목이다. 실행: `./gradlew :app:installDebug` 후 `adb shell am start -n com.example.dashpet/.MainActivity`.
`AndroidCarGateway` 는 `android.car` 를 직접 쓰므로 JVM 테스트로 검증할 수 없고, 아래 표의 결과로 대신한다(헌장 III).

## 환경

- 에뮬레이터: AVD `Pleos_Connect_v2.1.0`(API 34), Cold boot(`-no-snapshot-load -memory 4096`).
- 이 에뮬레이터에는 `hmg_car_service` 가 없어 `driving_regulation on|off` 명령을 쓸 수 없다. `cmd car_service get-property-value` 는 "requires non-user build" 로 막혀 있다.
- Virtual Console: `adb forward tcp:8088 tcp:8088` 후 `http://127.0.0.1:8088/`.
- 빌드: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.

## Virtual Console 조작 방법 (이 환경에서 확인한 것)

- 기어: `curl -X PUT "http://127.0.0.1:8088/vehicle/gear?gear=Park|Reverse|Neutral|Drive"`. 속도: `PUT /vehicle/controls?speed=..&accelerator=..&brake=..&steering=..`(페이지 스크립트 기준).
- 브라우저 탭이 열려 있지 않으면 `{"error":"emulator_inactive"}` 가 돌아온다. 콘솔 페이지가 1초마다 보내는 하트비트(`POST /browser/session?client=<id>`)를 보내면 `emulator_active=true`, `bridge_ready=true` 가 된다.
- 상태 확인: `curl http://127.0.0.1:8088/vehicle/state`.

## 이번 구현 중 알게 된 것

- **권한이 없으면 속도 속성이 "없는 것"처럼 보인다.** `CarPropertyManager.getCarPropertyConfig(PERF_VEHICLE_SPEED)` 는 `CAR_SPEED` 가 없는 앱에게 `null` 을 돌려주고(`propertyList` 도 6개뿐), `getProperty` 는 `SecurityException: requires android.car.permission.CAR_SPEED` 를 던진다. 그래서 `AndroidCarGateway` 는 **권한 확인을 설정 확인보다 먼저** 한다. 처음 순서로는 권한 거부가 `UNSUPPORTED` 로 잘못 기록됐다.
- **`CAR_SPEED` 권한 요청은 "위치 정보" 다이얼로그로 보인다.** `pm list permissions -g` 에서 `android.permission-group.LOCATION` 아래에 있다. 속도만 읽는데도 사용자에게는 위치 접근 요청이라 신뢰와 심사 문제가 될 수 있다. 대회 측 확인 사항에 추가한다.
- 속도 단위는 m/s 이고(`PERF_VEHICLE_SPEED`), 콘솔의 km/h 와 정확히 환산된다(40 → 11.111112, 72 → 20.0).

## 시나리오 결과 (`quickstart.md` 3절)

| # | 확인 방법 | 기대 결과 | 스펙 | 결과 |
|-|-|-|-|-|
| 1 | 앱을 열고 제한 값이 오기 전까지 본다 | 정적, 제한 `알 수 없음` 또는 첫 값 | US1-3, SC-002 | **확인함(2026-11-10).** 앱 시작 직후 `logcat -s CarSignal` 에 `link=CONNECTING` → `CONNECTED` → `restriction=ACTIVE bits=0x50` 이 6 ms 안에 순서대로 찍힌다. 값이 오기 전 화면은 정적(스크린샷: 모션 등급 "정적", 평평한 몸) |
| 2 | Virtual Console 에서 기어 P → D → P | 주차 여부가 바뀌고, 플랫폼 제한에 따라 1초 안에 정적 ↔ 풀 모션 | US1-1,2, SC-001, SC-003 | **제한 부분 확인함.** 기어 P(콘솔 `PUT /vehicle/gear?gear=Park`) → 플랫폼 `UXR: DO: false UxR: 0`, 앱 `restriction=NONE bits=0x0`, 화면 "풀 모션"(눈, 눈썹, 미소). D → `DO: true UxR: 80`, 앱 `ACTIVE bits=0x50`, 화면 "정적". P/D 10회 전환 모두 로그 반영까지 0.22초 이내(최대, 폴링 포함, 중앙값 0.05초) → SC-001 통과. 기어(주차 여부): P → `parked=true gear=4`, R → `parked=false gear=2`, D → `parked=false gear=8`, 초기 읽기도 앱 시작 직후 `gear=8` 로 한 번 전달됨(2026-11-10). |
| 3 | 기어 R, N | 둘 다 `주차 아님`, 4가지 모두 확인 | SC-003 | **통과(2026-11-10).** P `parked=true gear=4`, R `parked=false gear=2`, N(P 에서 전환) `parked=false gear=1`, D `parked=false gear=8`. 같은 주차 여부(R, N, D 사이)는 중복 제거로 로그가 남지 않는다. 4가지 모두 기대값 → SC-003 |
| 4 | 속도를 바꾼다 | 진단 속도 갱신, 속도 0 이고 P 여도 플랫폼 제한이 있으면 정적 | US2-2, SC-004 | **통과(2026-11-10).** 콘솔 `PUT /vehicle/controls?speed=40` → 앱 `speedMps=11.111112`(40 km/h), `speed=72` → `20.0`, `speed=0` → `moving=false`. 속도 0 + D 에서도 플랫폼 제한은 `0x50` 이라 앱도 `ACTIVE`(속도가 제한을 풀지 않음), P 로 바꾸면 `NONE` → SC-004. 진단 패널(US4)이 생기면 숫자도 화면에서 재확인 |
| 5 | `dumpsys car_service --services CarUxRestrictionsManagerService` 와 대조 | `UXR`, `DO` 가 진단과 일치 | US1 | **확인함.** `dumpsys car_service --services CarUxRestrictionsManagerService` 의 `UXR: DO: true UxR: 80` ↔ 앱 `bits=0x50`, `DO: false UxR: 0` ↔ `bits=0x0` |
| 6 | `CAR_SPEED` 회수 후 재시작 | 앱 정상, 속도 `권한 거부`, 기어와 제한은 동작 | US3-1, SC-005 | **통과(2026-11-10).** 권한을 회수하고 시작하면 `availability SPEED=NO_PERMISSION`, 런타임 다이얼로그에서 "허용 안함"을 눌러도 소스만 재시작되고 `SPEED=NO_PERMISSION` 유지, 앱 종료 없음(`FATAL EXCEPTION` 0건). 이후 콘솔에서 기어 P/D 를 바꾸면 `parked=true/false`, `restriction=NONE/ACTIVE` 가 계속 전달됨. "허용"을 누르면 소스가 재시작되고 `SPEED=OK` |
| 7 | 서비스 끊김 | 제한 `알 수 없음` → 정적, 복구 시 재읽기 | US3-2 | **환경에서 확인 불가.** 시스템 서비스(`com.android.car.updatable`)를 강제 종료하면 에뮬레이터 전체가 불안정해질 수 있어 시도하지 않았다. 가짜 게이트웨이 테스트로 대체: `Lost` → `Restriction(UNKNOWN)`, 복구 시 재읽기, 실행 중 게이트웨이 실패 시 제한 `UNKNOWN` 으로 되돌림(`CarSignalSourceTest`) |
| 8 | 화면 회전, 크기 변경 | 모드와 수신 유지 | US4-3, FR-018 | **통과(2026-11-10), 수정 후.** `wm density 360` / `wm size 1600x900` 으로 설정 변경을 만들었다. **수정 전**에는 Activity 가 다시 만들어져 `link=IDLE → CONNECTING → CONNECTED`(끊김 약 0.6~1.3초, 그동안 엔진이 새 세션이 되어 펫이 정적으로 돌아감)가 찍혔다. `MainActivity` 에 `configChanges`(orientation, screenSize, smallestScreenSize, screenLayout, density, uiMode, keyboardHidden, layoutDirection, fontScale, locale)를 선언한 **수정 후**에는 두 변경을 각각 2회 해도 `CarSignal` 로그가 없다(재연결 없음, `FATAL EXCEPTION` 0). 변경 상태에서도 콘솔 기어 변경이 계속 도착(`parked=false gear=8`, `restriction=ACTIVE bits=0x50`) |
| 9 | 홈으로 보냈다가 다시 연다 | 보이지 않는 동안 수신 없음, 돌아오면 현재 값 | US5-1,2 | **통과(2026-11-10).** 분할 윈도우에서는 Home, Back(`moveTaskToBack`), GNB 로 다른 앱을 열어도 이 앱이 계속 화면에 보여 `onStop` 이 오지 않는다(수신 유지가 정상). 전체 화면 윈도우(`am start --windowingMode 1 -a android.settings.SETTINGS`)로 완전히 덮으면 `link=IDLE`(수신 중단). 앱으로 돌아오면 `CONNECTING → CONNECTED` 후 `parked=false gear=8`, `restriction=ACTIVE bits=0x50` 을 한 번씩 다시 받는다 |
| 10 | 20회 열고 닫는다 | 클라이언트 수 누적 없음 | US5-3, SC-007 | **통과(2026-11-10).** 전체 화면 Settings 로 덮고 앱으로 돌아오기를 20회 반복: `link=IDLE` 20, `CONNECTING` 20, `CONNECTED` 20, `dumpsys car_service --services CarUxRestrictionsManagerService` 의 `callbacks:` 가 반복 전 13 → 반복 후 13(누적 없음), `FATAL EXCEPTION` 0 → SC-007 |
| 11 | 데모로 전환해 재생, 다시 실차로 | 데모 정상, 돌아온 뒤 데모 신호 없음, 10회 반복 | US4-1,2, SC-008, SC-009 | **통과(2026-11-10).** "데모" 칩 → `mode=DEMO`, 실차 소스 `link=IDLE`, 재생/제한 버튼이 나타남. 재생 0:10 지점에서 "감정: 기쁨"(초록, 정적). "실차" 칩 → `mode=VEHICLE`, `link=CONNECTING → CONNECTED`, 실제 값 `parked=false gear=8`, `restriction=ACTIVE bits=0x50` 을 다시 읽고 화면은 "감정: 편안, 모션 등급: 정적"(데모 상태가 남지 않음). 칩을 눌러 10회 번갈아 전환: `mode=DEMO` 10, `mode=VEHICLE` 10, `link=CONNECTED` 10, `link=IDLE` 10, `FATAL EXCEPTION` 0 → SC-009 |
| 12 | 실차 모드에서 수동 제한 토글 | 보이지 않거나 비활성 | FR-016 | **통과(2026-11-10).** 실차 모드 화면에는 모드 칩과 진단만 있고 재생, "제한 없음/있음/알 수 없음" 버튼이 없다. 데모 모드에서만 나타난다. (단위 테스트로도 실차 모드의 `setRestriction`, `togglePlay` 무시를 확인) |

## 미결 항목 (`research.md` R9)

| # | 항목 | 결과 |
|-|-|-|
| 1 | 표준 API/권한 사용 허용 여부 | **미결(대회 측 확인 필요).** 사용자에게 알린다 |
| 2 | `CAR_SPEED` 런타임 부여 방식 | **일부 확인(T023).** 이 에뮬레이터는 AAOS 멀티 유저라 앱이 사용자 10 으로 실행된다. `adb shell pm grant --user 10 com.example.dashpet android.car.permission.CAR_SPEED` 로 부여하면 속도가 들어온다(`--user` 없이 하면 사용자 0 에만 적용돼 효과 없음). `installDebug` 로 다시 설치하면 부여가 사라지므로 설치 뒤 다시 부여한다. 앱 안의 런타임 요청도 확인(T028~T029): 다이얼로그가 뜨고 "허용" 시 소스가 재시작돼 `SPEED=OK`, "허용 안함" 시 `NO_PERMISSION` 으로 계속 동작. **주의: 다이얼로그 문구가 "이 기기의 위치 정보에 액세스하도록 허용하시겠습니까?" 이다.** `android.car.permission.CAR_SPEED` 가 `android.permission-group.LOCATION` 그룹이라서 그렇다(앱은 위치를 읽지 않는다). 사용자에게는 위치 권한 요청처럼 보이므로 설명 문구나 심사 대응이 필요하다(헌장 V) |
| 3 | 제한 상태를 권한 없이 읽을 수 있는가 | **해결(T017).** `dumpsys package` 에서 `CAR_SPEED: granted=false` 인 채로 제한 상태를 읽고 변화를 받았다. 제한 읽기에는 권한이 필요 없다. manifest 의 두 권한은 속도와 기어용으로만 남는다 |
| 4 | 콜백 스레드 | |
| 5 | 속도 주기와 엔진 가정 | |
| 6 | `useLibrary("android.car")` 동작 | **해결(T001).** AGP 9.5 에서 `android { useLibrary("android.car") }` 만으로 `:signal:compileDebugKotlin` 이 통과한다. `compileOnly(files(...))` 대체는 필요 없었다 |

## 설정 변경 때 수신이 끊기던 문제 (US5 중 발견, 수정함)

`LifecycleStartEffect` 로 화면에 보일 때만 수신하게 하자 화면 설정이 바뀌어 Activity 가 다시 만들어질 때도 `onStop → onStart` 로 수신이 끊겼다 다시 열렸다. 연결을 새로 열면 엔진도 새 세션이 되어 감정 상태가 사라지고 제한을 다시 읽기 전까지 정적이 되므로 FR-018("모드와 수신이 이어진다")에 어긋난다. 유예 시간을 두는 대신 `configChanges` 로 재생성 자체를 막았다(타이밍에 의존하지 않음). 다른 사유의 재생성은 `ViewModel` 이 살아 있어 모드와 재생 상태는 유지되고, 수신만 새 세션으로 다시 연다.

## 진단 패널 레이아웃

진단 줄을 세로로 쌓으면 패널이 펫의 아랫부분을 가려서, 가로로 나란히 두고 폭이 모자랄 때만 줄바꿈하게 했다(스크린샷으로 확인: 펫이 온전히 보이고 패널 높이가 002 와 같은 수준).

## 자동 테스트와 성능

| 항목 | 결과 |
|-|-|
| 전체 테스트(`:signal :engine :pet :app` 의 `testDebugUnitTest`) + `assembleDebug` + `assembleRelease`(R8 최적화) | **통과(2026-11-10).** signal 91, engine 135, pet 106, app 35 = 367개 모두 통과, 실패 0. 기존 001, 002 테스트 포함(SC-008). 릴리즈 빌드도 성공 |
| 실차 모드, 제한 중(정적) CPU | **통과.** `top` 5회 샘플 0.0~1.0%(기준: 주행 중 5% 미만). `dumpsys gfxinfo` 프레임 0. `Tick` 250 ms 와 속도 5Hz 구독이 CPU 를 늘리지 않는다 |
| 실차 모드, 정차(P, 풀 모션) CPU | **기준 초과, 003 원인 아님.** `top` 35~64%, 10초 472프레임(Janky 5.5%). 같은 풀 모션을 데모 모드("제한 없음")에서 만들어도 29~53%, 482프레임(Janky 3.7%)으로 비슷하다. 002 의 풀 모션 렌더링 비용이고 에뮬레이터의 소프트웨어 렌더링 영향이 크다. AGENTS.md 의 "정차 중 25% 이하"는 실차에서 다시 재야 한다(`[확인 필요]`) |

### 측정 중 발견해 고친 문제

처음 측정에서 **제한 중(정적)인데도 CPU 약 16%, 10초에 88~115프레임**이 나왔다(데모 모드 정적은 0% 와 0프레임). 차량이 같은 속도 값을 초당 여러 번 알리는데, 중복으로 걸러내고도 진단의 `lastUpdateMs` 를 매번 갱신해서 "마지막 갱신" 글자가 계속 바뀌고 화면이 다시 그려졌다. `lastUpdateMs` 를 값이 실제로 바뀐 때만 갱신하게 고치고(회귀 테스트 3개: 같은 값 반복에는 진단이 새로 나오지 않음, 갱신 시각은 변경 때만 이동, 비트만 바뀌면 비트만 갱신) 다시 재서 0.5~1.0%, 0프레임이 됐다. 진단 패널은 디버그 빌드에서만 그려지지만, 값이 계속 바뀌는 상태 흐름은 릴리즈 빌드에서도 재구성을 일으킬 수 있어 원인 자체를 없앴다.

## AGENTS.md 12절 점검 (새 기능 체크)

| 항목 | 결과 |
|-|-|
| 권한 최소화 | **두 개만 선언**: `android.car.permission.CAR_SPEED`(속도, 런타임), `CAR_POWERTRAIN`(기어, normal). 제한 상태는 권한 없이 읽는다(확인함). 승인되지 않은 권한 선언 여부는 미결 사항 1 |
| Capability 확인 | 차종명과 지역으로 분기하지 않는다. 속성 설정의 존재(`getCarPropertyConfig`)로 지원을 판단하고, 권한 없음과 미지원을 구분한다(권한 확인이 먼저) |
| 주행 규제 대응 | 제한 상태는 플랫폼 `CarUxRestrictions` 만 근거로 하고(`requiresDo \|\| bits != 0`) 속도와 기어는 제한 판단에 쓰지 않는다. 모르면 `UNKNOWN`(정적): 시작 직후, 권한 없음, 서비스 없음, 끊김, 게이트웨이 실패. 에뮬레이터에서 P/D 10회 전환이 모두 0.22초 이내 반영 |
| 오디오 포커스 | 해당 없음(음성 없음) |
| 가로/세로 대응 | 설정 변경에서 Activity 를 다시 만들지 않고(`configChanges`) 수신과 엔진 상태가 이어진다. `wm density`, `wm size` 로 확인. 실제 가로↔세로 전환(2 윈도우 ↔ 1 윈도우)은 이 에뮬레이터에서 만들지 못했다 |
| CCP Back | 변경 없음(`MainActivity` 의 `moveTaskToBack(true)` 유지). 분할 윈도우에서는 Back 후에도 앱이 계속 보이는 것을 확인 |
| `release` 시점 | 수집이 끝날 때(`awaitClose`) 리스너 해제와 `Car.disconnect()`. 화면에서 사라지면 수집을 멈추고, 돌아오면 새 `Car` 를 만든다. 20회 반복에서 클라이언트 수 누적 없음(13 → 13) |
| 비밀값 비노출 | 비밀값 없음. 로그는 상태 변화만 한 줄씩(`CarSignal`), 속도는 정지↔주행 전환 때만. 위치와 개인정보를 읽거나 저장하지 않는다 |
| 검증 방법 제시 | `quickstart.md`(에뮬레이터 Cold boot, Virtual Console HTTP 조작, `dumpsys car_service` 대조) |
| 스펙 범위 밖(변경 없음) | 첫 실행 동의 화면(헌장 V)은 온보딩 기능의 몫이라 이번 범위 밖이다. 이 기능은 값을 저장하지 않는다 |
