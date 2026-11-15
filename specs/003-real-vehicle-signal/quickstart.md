# Quickstart: 실차 신호 연결 검증

**Feature**: [spec.md](./spec.md) | **Contract**: [contracts/car-signal-source.md](./contracts/car-signal-source.md)

구현이 끝난 뒤 이 순서로 확인한다. 결과는 `checklists/verification-log.md` 에 남긴다(헌장 III).
시그니처와 변환 규칙은 [data-model.md](./data-model.md) 를 본다.

## 0. 사전 준비

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"   # 시스템 JDK 없음
export ADB=~/Library/Android/sdk/platform-tools/adb
```

Pleos Connect 에뮬레이터를 Cold boot 로 띄운다(API 34, RAM 4 GB, AVD `Pleos_Connect_v2.1.0`).

```bash
~/Library/Android/sdk/emulator/emulator -avd Pleos_Connect_v2.1.0 -no-snapshot-load -memory 4096 &
$ADB wait-for-device && $ADB forward tcp:8088 tcp:8088     # Virtual Console: http://127.0.0.1:8088/
```

브라우저 없이 콘솔을 조작하려면 페이지가 하는 하트비트를 대신 보내야 한다(없으면 `{"error":"emulator_inactive"}`).

```bash
( while true; do curl -s -X POST "http://127.0.0.1:8088/browser/session?client=cli" >/dev/null; sleep 1; done ) &
curl -s -X PUT "http://127.0.0.1:8088/vehicle/gear?gear=Park"          # Park | Reverse | Neutral | Drive
curl -s -X PUT "http://127.0.0.1:8088/vehicle/controls?speed=40&accelerator=0&brake=0&steering=0"   # km/h
```

## 1. 자동 테스트(차량 없이)

```bash
./gradlew :signal:test :engine:test :pet:test :app:testDebugUnitTest
```

기대 결과: 모두 통과. 특히 다음이 포함돼야 한다.

- `CarSignalMapperTest`: 속도 단위와 비정상 값 거부, 기어와 주차 여부(P, D, R, N, 알 수 없음), 제한 매핑(`requiresDo`, 비트 조합), 시각 비감소(SC-006).
- `CarSignalSourceTest`(가짜 게이트웨이): 시작 직후 현재 값 한 번, 중복 제거, `Lost` 시 `UNKNOWN`, 복구 시 재읽기, 수집 취소 시 해제, 권한 거부와 미지원에서 나머지 신호 유지.
- `SwitchableSignalSourceTest`: 전환 중 두 소스 신호가 섞이지 않음(SC-009).
- 기존 001, 002 테스트가 그대로 통과(SC-008).

## 2. 설치와 첫 실행

```bash
./gradlew :app:installDebug
$ADB shell am start -n com.example.dashpet/.MainActivity
$ADB shell pm grant --user 10 com.example.dashpet android.car.permission.CAR_SPEED   # 또는 앱의 다이얼로그에서 허용. 앱은 사용자 10 으로 실행되므로 --user 10 이 필요하고, installDebug 로 다시 설치하면 사라진다
$ADB logcat -s CarSignal
```

기대 결과: 앱이 종료되지 않고 열린다. 진단 패널의 모드가 `실차` 이고 연결 상태가 `연결됨` 으로 바뀐다. `logcat` 에 `link=CONNECTING → CONNECTED`, `parked=..`, `restriction=..` 이 찍힌다.
`CAR_SPEED` 가 없으면 `availability SPEED=NO_PERMISSION` 이 찍히고 앱 안에 "위치 정보" 문구의 권한 다이얼로그가 뜬다(research R9 "새로 알게 된 것").
이 에뮬레이터에는 `hmg_car_service` 가 없으므로(research R8) `driving_regulation on|off` 명령은 쓰지 않는다.

## 3. 시나리오별 확인

| # | 하는 일 | 기대 결과 | 스펙 |
|-|-|-|-|
| 1 | 앱을 열고 제한 값이 오기 전까지 펫 화면을 본다 | 정적. 모션 등급 `정적`, 제한 `알 수 없음` 또는 첫 값 | US1-3, SC-002 |
| 2 | Virtual Console 에서 기어 P → D → P | 기어 `주차 아님` ↔ `주차`로 진단 값이 바뀐다. D 에서 플랫폼 제한이 걸리면 1초 안에 정적, P 에서 풀리면 풀 모션 | US1-1,2, SC-001, SC-003 |
| 3 | 기어 R, N 으로 바꾼다 | 둘 다 `주차 아님`. 4가지 모두 확인 | SC-003 |
| 4 | 속도를 바꾼다(`/vehicle/controls?speed=40`) | 진단 속도가 바뀌고 환산이 맞다(40 km/h → 11.1 m/s, 72 → 20.0). 속도 0 이고 D 여도 플랫폼 제한이 있으면 정적 | US2-2, SC-004 |
| 5 | 플랫폼 쪽 값과 대조한다 | `$ADB shell dumpsys car_service --services CarUxRestrictionsManagerService` 의 `UXR` 와 `DO` 가 진단의 제한 비트와 일치 | US1 |
| 6 | 권한 거부 상태로 연다(`pm revoke --user 10 com.example.dashpet android.car.permission.CAR_SPEED` 후 재시작, 다이얼로그는 "허용 안함") | 앱은 정상. 속도는 `권한 거부`, 기어와 제한은 계속 동작 | US3-1, SC-005 |
| 7 | 서비스가 끊긴 상황(시스템 서비스를 강제 종료하면 에뮬레이터가 불안정해질 수 있어 이 환경에서는 시도하지 않는다. `CarSignalSourceTest` 의 가짜 게이트웨이 테스트로 대체) | 제한이 `알 수 없음` 으로 바뀌어 정적. 복구되면 현재 값을 다시 읽고 정상으로 | US3-2 |
| 8 | 화면 설정 변경(`$ADB shell wm density 360`, `wm size 1600x900`, 끝나면 `wm density reset`, `wm size reset`) | 모드와 수신이 이어진다. `logcat -s CarSignal` 에 재연결(`IDLE`, `CONNECTING`)이 없다 | US4-3, FR-018 |
| 9 | 전체 화면 윈도우로 덮었다가 다시 연다(`$ADB shell am start --windowingMode 1 -a android.settings.SETTINGS`, 이어서 앱 실행). 분할 윈도우에서는 Home, Back, GNB 로 다른 앱을 열어도 이 앱이 계속 보여 `onStop` 이 오지 않는다 | 보이지 않는 동안 수신 없음(`link=IDLE`), 돌아오면 현재 값이 한 번씩 전달됨 | US5-1,2 |
| 10 | 위 덮기와 다시 열기를 20회 반복한다 | `$ADB shell dumpsys car_service --services CarUxRestrictionsManagerService` 의 `callbacks:` 수가 반복 전후 같다. `IDLE`, `CONNECTING`, `CONNECTED` 가 각각 20 | US5-3, SC-007 |
| 11 | 모드를 `데모` 로 바꿔 시나리오를 재생하고 다시 `실차` 로 돌아온다 | 데모는 기존처럼 동작(재생, 수동 제한). 돌아온 뒤 데모 신호가 남지 않고 실차 값에서 새로 시작. 10회 반복 | US4-1,2, SC-008, SC-009 |
| 12 | 실차 모드에서 수동 제한 토글을 누른다 | 보이지 않거나 비활성. 엔진 입력에 영향 없음 | FR-016 |

## 4. 성능과 안전 점검

- 주행 제한이 걸린 동안(D단) 프레임이 0이고 CPU 가 5% 미만인지 본다: `$ADB shell dumpsys gfxinfo com.example.dashpet reset`, 10초 뒤 `dumpsys gfxinfo` 의 `Total frames rendered`, 그리고 `$ADB shell "top -b -n 5 -d 2 -p $(pidof com.example.dashpet)"`. 앱이 시작한 직후 몇 초는 빼고 잰다. 측정 결과(2026-11-10): 0 프레임, 0.5~1.0%.
- 같은 값이 반복돼도 진단이 바뀌지 않는지가 핵심이다. 이게 깨지면 정적 상태에서도 화면이 계속 다시 그려진다(CPU 16%, 약 9fps).
- 정차(P단, 풀 모션)의 CPU 는 에뮬레이터에서 35~64% 로 나온다. 데모 모드의 풀 모션도 같다(29~53%)이므로 002 의 렌더링 비용이다. 25% 기준은 실차에서 다시 재야 한다.

## 5. 미결 항목이 풀렸는지

[research.md](./research.md) R9 의 6건 결과는 이미 적혀 있다. 남은 것은 ① 표준 API 와 `android.car.permission.*` 허용 여부(대회 측 확인, 위치 권한 그룹 문제 포함) ② 가속 이벤트 품질(실차 주행에서) ③ 풀 모션 CPU(실차)이다.
