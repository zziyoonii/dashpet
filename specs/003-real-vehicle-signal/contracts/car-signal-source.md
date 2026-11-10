# Contract: 실차 신호 소스

**Feature**: [spec.md](../spec.md) | **Data model**: [data-model.md](../data-model.md)

이 기능이 만드는 내부 인터페이스의 약속이다. 외부에 공개하는 API 는 없다. 시그니처 전체가 아니라 지켜야 할 동작을 적는다.
(구현 후 갱신: 2026-11-10. 계획 때와 달라진 점은 각 절에 "구현에서" 로 표시했다.)

## 1. `VehicleSignalSource` 약속(기존, 그대로)

`CarSignalSource` 는 기존 인터페이스를 그대로 지킨다. 엔진은 소스 종류를 모른다.

- `signals`: 시간순 `RawSignal` 스트림, 같은 스트림 안에서 `timestampMs` 는 감소하지 않는다.
- `start()` 전에는 방출하지 않는다. `stop()` 뒤에는 멈춘다. 다시 `start()` 하면 새로 시작한다.
- 엔진은 `signals` 를 먼저 수집한 뒤 `start()` 를 부른다(`DrivingEngine.start`). 수집이 시작되기 전의 `start()` 도, 수집 뒤의 `start()` 도 같은 결과여야 한다.

## 2. `CarSignalSource` 의 동작

| 순서 | 사건 | 결과 |
|-|-|-|
| 1 | `start()` 와 수집 시작 | 게이트웨이 연결을 시작한다. `link=CONNECTING` |
| 2 | `Ready` | `link=CONNECTED`. **구현에서**: 현재 값 읽기는 소스가 아니라 게이트웨이가 한다. 게이트웨이는 `Ready` 직후 속도, 기어, 제한의 현재 값을 `SpeedChanged`, `GearChanged`, `RestrictionChanged` 로 한 번씩 내보내고(이 순서), 소스는 그것을 변환해 보낸다 |
| 3 | 값 변화 | 변환 규칙을 거쳐 보낸다. 직전에 보낸 값과 같으면 보내지 않는다 |
| 4 | `Tick` | `tickIntervalMs`(기본 250) 마다 `Tick(timestamp)` 를 보낸다 |
| 5 | `Lost` | `Restriction(UNKNOWN)` 을 보낸다(이미 UNKNOWN 이면 보내지 않음). `link=LOST`. 다음 `Ready` 에서 2번부터 다시 |
| 6 | 게이트웨이 흐름이 예외로 끝남 | 예외를 밖으로 던지지 않는다. `Restriction(UNKNOWN)` 을 보내고 `link=UNAVAILABLE`. **구현에서 추가**: 엔진이 이전 값(예: 제한 없음)에 머물면 풀 모션이 계속되므로 안전 쪽으로 되돌린다 |
| 7 | `stop()` 또는 수집 취소 | 리스너를 모두 해제하고 연결을 끊는다. `link=IDLE`. 이후 이벤트는 흘려보내지 않는다 |

- 같은 시각의 신호 순서는 게이트웨이가 낸 순서를 따른다: 속도, 주차, 제한, 그 뒤 `Tick`.
- 개별 신호가 `UNSUPPORTED`, `NO_PERMISSION`, `ERROR` 면 그 신호만 생략하고 나머지는 보낸다. 앱은 종료되거나 예외를 던지지 않는다.
- 제한 신호를 한 번도 받지 못한 동안 엔진은 `UNKNOWN`(정적)이다. 소스가 이를 위해 신호를 따로 보낼 필요는 없다.
- 시각 붙이기와 보내기는 한 락 안에서 한다. 차량 콜백이 어느 스레드에서 와도 시각 순서가 곧 출력 순서다.
- **진단 값은 엔진에 새 값이 가지 않는 반복에는 바뀌지 않는다.** 차량은 같은 값을 자주 알리므로(속도는 초당 여러 번) 이때 진단 값을 새로 내면 정적 상태에서도 이 값을 보는 화면이 계속 다시 그려진다(에뮬레이터 측정: CPU 16%, 약 9fps). `lastUpdateMs` 는 값이 실제로 바뀐 시각이다.
- 로그(`CarSignal` 태그)는 연결 상태 변화, 제한 값 변화, 주차 여부 변화, 정지↔주행 전환, 신호별 가용 상태만 한 줄씩 남긴다. 자주 오는 속도 값은 남기지 않는다.

## 3. `CarGateway` 약속(차량 서비스 접근)

- 이 인터페이스 밖으로 `android.car` 타입이 새지 않는다(`Car`, `CarPropertyManager`, `CarUxRestrictions` 는 `AndroidCarGateway` 안에만).
- `events()` 는 수집이 시작되면 연결하고, 수집이 끝나면 `Car.disconnect()` 와 모든 리스너 해제를 한다(`awaitClose`).
- 콜백은 이 세션 전용 스레드(`HandlerThread`)에서 받고 `trySend` 로 넘긴다. 어느 스레드에서 와도 소스가 순서를 지킨다.
- 신호마다 리스너를 **먼저 등록한 뒤** 현재 값을 읽어, 그 사이의 변화를 놓치지 않는다(같은 값은 소스가 걸러낸다).
- 속도는 **권한 확인이 설정 확인보다 먼저**다. 플랫폼은 `CAR_SPEED` 가 없는 앱에게 속도 속성 설정을 아예 숨기므로(`getCarPropertyConfig` 가 null), 순서가 바뀌면 권한 거부가 "지원 안 함"으로 보인다. 권한이 있는데 설정이 없을 때만 `UNSUPPORTED`.
- 지원 여부는 속성 설정의 존재로 판단한다. 차종명, 플랫폼 이름, 지역으로 분기하지 않는다.
- 읽기와 구독 호출의 예외(`SecurityException` → `NO_PERMISSION`, 그 밖 → `ERROR`)는 해당 신호만 표시하고 계속한다. 콜백 안에서 예외가 새지 않는다.
- 서비스가 끊기면(`onLifecycleChanged(.., false)`) 관리자 객체를 해제하고 `Lost` 를 보낸다. 복구되면(`true`) 관리자 객체를 새로 얻어 다시 등록하고 현재 값을 읽는다.
- 해제한 `Car` 를 다시 쓰지 않는다. 다시 수집하면 새 `Car` 를 만든다.

## 4. `SwitchableSignalSource` 약속

- `select(source)`: 이전 소스의 `stop()` 이 끝난 뒤에 새 소스의 신호만 내보낸다. 두 소스의 신호가 섞여 나가지 않는다(SC-009).
- 선택된 소스가 같으면 아무것도 하지 않는다.
- `start()`/`stop()` 은 현재 선택된 소스에 위임한다. 새 소스의 `start()` 는 호출하는 쪽의 책임이다.

## 5. 앱 쪽 약속(`DemoViewModel`)

**구현에서**: 진단은 별도 인터페이스(`DiagnosticSource`)가 아니라 생성자 인자 `diagnostics: StateFlow<SignalDiagnostics>?` 로 받는다. 로그는 `log: (String) -> Unit` 으로 주입한다(단위 테스트가 Android 없이 돌도록).

| 호출 | 동작 |
|-|-|
| `setMode(mode)` | 같은 모드면 무시. 아니면 `engine.stop()` → `SwitchableSignalSource.select` → `engine.start()`. 재생과 수동 제한을 초기화한다. `VEHICLE` 로 갈 때는 화면에 보이는 중일 때만 시작한다 |
| `onForeground()` | `VEHICLE` 모드면 `engine.start()`(새 세션: 현재 값을 한 번씩 다시 읽음). 이미 수신 중이면 아무것도 하지 않는다 |
| `onBackground()` | `VEHICLE` 모드면 `engine.stop()`(연결 정리). `DEMO` 모드는 건드리지 않는다 |
| `onSpeedPermissionResult(granted)` | `VEHICLE` 모드에서 화면에 보이는 중이면 `engine.stop()` → `engine.start()` 로 재시작한다(소스는 시작할 때마다 권한을 새로 확인). 그 밖에는 아무 일도 하지 않는다 |
| `setRestriction(state)` | `VEHICLE` 모드에서는 무시한다(FR-016). `DEMO` 모드는 기존 동작 |
| `togglePlay()`, `stop()` | `DEMO` 모드에서만 동작한다 |

- 엔진이 도는 때: `DEMO` 는 항상(수동 제한 주입이 항상 처리되도록), `VEHICLE` 은 화면에 보이는 동안만.
- 화면 회전과 크기 변경: **구현에서** `MainActivity` 에 `configChanges` 를 선언해 Activity 가 다시 만들어지지 않는다. 재생성되면 `onStop → onStart` 로 수신이 끊겼다 다시 열리고 엔진 상태가 초기화됐다(측정: 0.6~1.3초 끊김). 다른 사유의 재생성은 `ViewModel` 이 살아 있어 모드와 재생 상태가 유지되고 수신만 새 세션으로 다시 열린다.
- `DemoState.diagnostics` 는 `VEHICLE` 모드에서만 값이 있고 `DEMO` 에서는 null 이다.

## 6. 진단 표시 약속(`DemoControls`, 디버그 빌드의 패널)

`debugPanelVisible` 이 켜져 있을 때 `signalDiagnosticLines(d)`(순수 함수) 결과를 가로로 나란히(폭이 모자랄 때만 줄바꿈) 보여준다. 세로로 쌓으면 패널이 펫을 가린다.

- `연결: 대기|연결 중|연결됨|사용 불가|끊김`
- `제한: 있음|없음|알 수 없음 (0x<비트>)` + 이유
- `속도: <km/h> (<m/s>)` 또는 `-` + 이유
- `기어: 주차|주차 아님|-` + 이유
- `마지막 갱신: <초>` — 값이 실제로 바뀐 시각(소스 시작 기준)
- 이유: `확인 중`(UNKNOWN), `권한 거부`, `미지원`, `오류`. `OK` 는 이유를 붙이지 않는다.
- 모드 선택 칩(`실차`/`데모`)은 선택된 칩에 `✓` 를 붙인다. 실차 모드에서는 재생과 수동 제한 버튼을 숨긴다.

## 7. manifest 약속

```xml
<uses-permission android:name="android.car.permission.CAR_SPEED" />
<uses-permission android:name="android.car.permission.CAR_POWERTRAIN" />
```

- 이 두 개만 선언한다. 승인되지 않은 권한 추가 금지(헌장 IV). `CAR_SPEED` 는 런타임 권한이고 앱이 실차 모드로 열릴 때 한 번 요청한다(`HomeScreen`, 회전해도 다시 묻지 않음). 거부해도 앱은 속도 없이 동작한다.
- 제한 상태 읽기에는 권한이 필요 없다(에뮬레이터에서 `CAR_SPEED` 미부여 상태로 확인).
- **주의**: `CAR_SPEED` 는 `android.permission-group.LOCATION` 그룹이라 다이얼로그가 "이 기기의 위치 정보에 액세스하도록 허용하시겠습니까?" 로 보인다. 앱은 위치를 읽지 않는다(헌장 V).
- `MainActivity` 에 `android:configChanges="orientation|screenSize|smallestScreenSize|screenLayout|density|uiMode|keyboardHidden|layoutDirection|fontScale|locale"` 를 둔다.
