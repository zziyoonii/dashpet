# Quickstart: 말풍선과 음성 검증

**Feature**: [spec.md](./spec.md) | **Contract**: [contracts/speech.md](./contracts/speech.md)

구현이 끝난 뒤 이 순서로 확인한다. 결과는 `checklists/verification-log.md` 에 남긴다(헌장 III). 문장과 규칙은 [contracts/speech.md](./contracts/speech.md), 타입은 [data-model.md](./data-model.md) 를 본다.

## 0. 사전 준비

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"   # 시스템 JDK 없음
export ADB=~/Library/Android/sdk/platform-tools/adb
```

Pleos Connect 에뮬레이터를 Cold boot 로 띄운다(API 34, RAM 4 GB, AVD `Pleos_Connect_v2.1.0`).

```bash
~/Library/Android/sdk/emulator/emulator -avd Pleos_Connect_v2.1.0 -no-snapshot-load -memory 4096 &
$ADB wait-for-device && $ADB forward tcp:8088 tcp:8088     # Virtual Console: http://127.0.0.1:8088/
( while true; do curl -s -X POST "http://127.0.0.1:8088/browser/session?client=cli" >/dev/null; sleep 1; done ) &
```

기어와 속도는 003 과 같다: `PUT /vehicle/gear?gear=Park|Reverse|Neutral|Drive`, `PUT /vehicle/controls?speed=<kph>&accelerator=0&brake=0&steering=0`.
주의: R단에서는 플랫폼이 후방 카메라를 앞에 띄워 이 앱이 가려진다(003 검증 기록). R 은 이 기능의 확인 대상이 아니다.

## 1. 자동 테스트(차량 없이)

```bash
./gradlew :signal:test :engine:test :pet:test :app:testDebugUnitTest
```

기대 결과: 모두 통과. 특히 다음이 포함돼야 한다.

- `SpeechCatalogTest`: 이벤트 10종 모두 문장과 우선순위가 있음, 모든 `text` 가 120자, 24어절, 한 줄 80자, 3줄 이하, 모든 `shortText` 가 `restrictedMaxChars` 이하, 자리 표시자 없음, 변형 2개가 서로 다름(SC-002).
- `SpeechDirectorTest`: 쿨다운, 우선순위 선점(위험 경고가 칭찬을 끊음), 대기 슬롯과 만료(2초), 같은 변형 연속 금지, `FULL → STATIC` 에서 말풍선 지움, `STATIC` 에서는 `showBubble=false` 와 짧은 문장, `reset` 후 상태 없음(SC-004, SC-006).
- `SpeechPlayerTest`(가짜 포커스와 가짜 합성기): 포커스 요청 → 선행 무음 → 말하기 순서, 포커스 변경 4가지 각각의 동작과 0.5초 안 중지, 실패/오류/감시 시간 초과에서도 포커스 반납이 요청 횟수와 같음, `UNAVAILABLE` 에서 예외 없음(SC-005, SC-007, SC-009).
- `SpeechCoordinatorTest`(app): 화면에 보이지 않는 동안 말하지 않음, 음성 끄기 시 소리 없고 말풍선만, 제한 중 설정 변경 무시, 모드 전환 뒤 말풍선과 대기가 남지 않음(FR-011, FR-016, FR-017).
- 기존 001~003 테스트가 그대로 통과(SC-008).

## 2. 설치와 첫 실행

```bash
./gradlew :app:installDebug
$ADB shell pm grant --user 10 com.example.dashpet android.car.permission.CAR_SPEED     # 003 과 같다. 설치마다 다시 부여한다
$ADB shell am start -n com.example.dashpet/.MainActivity
$ADB logcat -s Speech CarSignal
```

기대 결과: 앱이 종료되지 않고 열린다. `logcat -s Speech` 에 음성 가용 상태(`voice=READY` 또는 `UNAVAILABLE` 과 이유)가 한 줄 찍힌다.

**스파이크(구현 첫 작업)**: 이 에뮬레이터에 음성 합성 서비스(CAAS)와 `pleos.car.permission.TTS_SERVICE` 가 있는지 먼저 본다.

```bash
$ADB shell pm list packages | grep -i -E "caas|tts|speech"
$ADB shell pm list permissions | grep -i TTS_SERVICE
```

없으면 `voice=UNAVAILABLE` 로 떨어지는 것이 정상이고, 이후 음성 소리 확인은 실차로 미룬다(미결로 기록). 말풍선과 규칙은 그대로 에뮬레이터에서 확인한다.

## 3. 시나리오별 확인

데모 모드로 바꾸면 이벤트 시나리오를 재생할 수 있다(`데모` 칩 → 재생). 데모 시나리오는 제한 없음/있음을 번갈아 만든다.

| # | 하는 일 | 기대 결과 | 스펙 |
|-|-|-|-|
| 1 | 실차 모드, 기어 P(제한 없음)에서 데모 이벤트와 같은 상황을 만든다(데모 모드 재생으로 대체) | 이벤트가 판정되면 1초 안에 말풍선이 나타나고(`FULL` 일 때) 같은 문장이 음성으로 나온다 | US1-1, SC-001 |
| 2 | 위험 경고와 칭찬 이벤트를 각각 만든다 | 문장 톤이 다르고 점수나 비난이 없다. 문장은 [contracts/speech.md](./contracts/speech.md) 1절과 같다 | US1-2 |
| 3 | 같은 이벤트를 짧은 간격으로 반복한다 | 쿨다운 안에서는 말하지 않고, 지난 뒤에는 직전과 다른 변형 문장이 나온다 | US1-3, US4-4, FR-014 |
| 4 | 말풍선을 터치하지 않고 둔다 | `bubbleDurationMs`(초안 4초) 뒤에 사라진다 | US1-4 |
| 5 | 기어를 D 로 바꿔 제한을 건 뒤(실차 모드) 이벤트를 만든다 | 말풍선 글자가 나타나지 않고, 한 문장 음성과 펫 색 변화만 나온다. 화면 프레임 0, CPU 5% 미만 | US2-1, SC-003 |
| 6 | 말풍선이 떠 있을 때 기어를 D 로 바꾼다 | 1초 안에 말풍선이 움직임 없이 사라진다 | US2-3, SC-004 |
| 7 | 제한 중에 화면을 터치한다 | 아무 일도 일어나지 않는다 | US2-4 |
| 8 | 제한 상태를 알 수 없는 상태(앱 시작 직후, 첫 값 전)에 이벤트를 만든다 | 제한 있음과 같은 규칙(글자 없음) | US2-5 |
| 9 | 음악을 재생한 상태에서 펫이 말하게 한다(에뮬레이터 미디어 앱 사용) | 포커스를 요청하고 말한 뒤 반납한다. 음악이 끊기는지 duck 되는지 기록한다(research R4 `[확인 필요]`) | US3-1 |
| 10 | 말하는 중 다른 앱이 포커스를 가져간다(통화 또는 다른 오디오 앱) | 0.5초 안에 멈추고 자동으로 다시 말하지 않는다 | US3-2,4, SC-005 |
| 11 | 앱을 전체 화면 윈도우로 덮는다(`am start --windowingMode 1 -a android.settings.SETTINGS`) | 재생 중이던 음성이 멈추고 포커스가 반납된다. 덮인 동안 이벤트가 와도 말하지 않는다 | US3-6, FR-011 |
| 12 | 이벤트를 연속으로 만든다(위험 경고 직후 칭찬 등) | 위험 경고가 우선하고, 대기 중인 오래된 말은 버려진다 | US4-1~3, SC-006 |
| 13 | 음성 합성을 쓸 수 없는 상태에서(`TTS_SERVICE` 회수 또는 서비스 없음) 이벤트 100개를 만든다 | 앱이 종료되지 않고 오류 화면이 없다. 제한 없음이면 말풍선은 정상 | US5-1, SC-007 |
| 14 | 음성을 끈다(제한 없음 상태에서) | 소리 없이 말풍선만. 앱을 다시 열어도 유지. 제한 중에는 설정이 막힌다 | US5-2,3, FR-016 |
| 15 | 가렸다가 다시 여는 것을 20번 반복한다 | 포커스 요청 횟수와 반납 횟수가 같다. `Speech` 로그의 요청과 반납이 각각 같은 수 | SC-009 |
| 16 | 모드를 실차 ↔ 데모로 10번 바꾼다 | 말풍선과 대기 중인 음성이 남지 않는다. 크래시 없음 | FR-017 |
| 17 | `wm density 360`, `wm size 1600x900` 로 설정을 바꾼다(끝나면 `reset`) | 말풍선이 잘리지 않고 재생 중인 음성이 끊기지 않는다 | FR-018 |

## 4. 성능과 안전 점검

- 제한 중(D단) 이벤트를 발생시키는 동안 `$ADB shell dumpsys gfxinfo com.example.dashpet reset`, 10초 뒤 `Total frames rendered` 가 거의 0 인지 본다(색 변화 순간 제외). CPU 는 `$ADB shell "top -b -n 5 -d 2 -p $(pidof com.example.dashpet)"` 로 5% 미만인지 본다.
- 말풍선이 없을 때 이 기능이 새로 그리는 것이 없는지 본다(말풍선 노드가 없고 프레임이 늘지 않아야 한다).
- 풀 모션 CPU(P단)의 기존 문제(003 미결 3번)는 이 기능과 별개다. 말풍선이 더해져 얼마나 늘었는지만 비교해서 기록한다.

## 5. 미결 항목이 풀렸는지

[research.md](./research.md) 의 미결 요약 7건 중 에뮬레이터에서 풀리는 것은 1, 3(일부), 6(값 초안 확인)이다. 2(권한 승인), 4(첫 음절 잘림), 5(INTERNET 권한), 7(경고음 감지)은 대회 측 확인이나 실차가 필요하다. 결과를 `verification-log.md` 에 적고 사용자에게 알린다.
