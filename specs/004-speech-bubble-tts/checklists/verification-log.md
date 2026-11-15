# 검증 기록: 말풍선과 음성(TTS)

**작성**: 2026-11-13 | **범위**: 단위 테스트와 정적 점검. 에뮬레이터 확인은 하지 못했다.

## 자동 테스트

명령: `./gradlew :signal:testDebugUnitTest :engine:testDebugUnitTest :pet:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug`

| 영역 | 결과 | 근거 |
|-|-|-|
| 카탈로그(문장 길이, 어절, 줄, 자리 표시자, 변형 2개, 우선순위 표) | 통과 | `SpeechCatalogTest` (SC-001, SC-002) |
| 규칙(쿨다운, 선점, 대기, 만료, 변형 교대, 도착 한 번, `FULL→STATIC` 지움, `reset`) | 통과 | `SpeechDirectorTest` (SC-004, SC-006) |
| 플레이어(순서, 포커스 4가지, 0.5초 안 중지, 감시 시간, 오류, BUSY, 불가, 20회 반복 요청=반납) | 통과 | `SpeechPlayerTest` (SC-005, SC-007, SC-009) |
| 코디네이터(말풍선, 제한 중 글자 없음, 지움, 선점, 화면 사라짐, 20회 반복, 음성 끄기, 제한 중 설정 무시, 불가 100개, `reset`) | 통과 | `SpeechCoordinatorTest` |
| 기존 001~003 테스트 | 통과 | 위 명령 전체가 실패 없이 끝남 (SC-008) |

## 에뮬레이터

| 항목 | 결과 |
|-|-|
| 부팅(Cold boot, RAM 4 GB) | **실패**. 2회 시도(기본 GPU, `-gpu swiftshader_indirect`) 모두 `Failed to find ColorBuffer`, `hanging thread 'QEMU2 main loop'` 로 멈춤. 남은 프로세스는 정리함 |
| 음성 서비스(CAAS)와 `TTS_SERVICE` 유무 (T001 스파이크) | 미확인 |
| `quickstart.md` 3절 17개 시나리오, 4절 성능(프레임, CPU) | **미확인** |
| 소리, 포커스 4가지 실제 변경, 선행 무음의 첫 음절 보호 | **미확인**(에뮬레이터 또는 실차 필요) |

## 정적 점검

| 항목 | 결과 |
|-|-|
| SDK import 는 `PleosSpeechOutput.kt` 한 파일 | 확인 (`rg "ai.pleos.playground.tts"`) |
| Client ID/Secret 없음 | 확인 (`ON_DEVICE`, 소스에 키 없음) |
| 로그에 문장 본문 없음 | 확인 (이벤트 종류, 이유, 상태만) |
| 병합 매니페스트의 권한 | `CAR_SPEED`, `CAR_POWERTRAIN`, `TTS_SERVICE`(우리 선언), SDK 가 병합한 `VEHICLE_BINDING`, `INTERNET`, 동적 리시버 권한 |
| `SpeechBubble` | 터치 처리와 애니메이션 없음, `null` 이면 그리지 않음(코드 점검). Compose UI 테스트 환경이 없어 자동 검증은 아님 |
| 제한 중 색 변화 | 이벤트 10종 중 8종은 감정 색이 바뀜. `TRIP_START`, `NO_SIGNAL_LANE_CHANGE` 는 색 변화 없음(research 8) |
| 가로/세로 대응 | 코드만(`widthIn(max)`, `safeDrawingPadding`). 화면 확인 안 함 |

## AGENTS.md 12절 체크

권한 최소화(1개 추가), Capability 확인(`UNAVAILABLE` 로 떨어짐), 주행 규제(제한 중 글자 없음, 터치 없음, 애니메이션 없음), 오디오 포커스(요청/반납 짝, 4가지 처리), 가로/세로(미확인), CCP Back(기존 유지), release 시점(화면에서 사라질 때 해제, 해제한 인스턴스 재사용 없음), 비밀값(없음).

## 에뮬레이터 확인 (2026-11-14, 일부)

부팅: 화면 있는 모드(기본 GPU, `swiftshader_indirect`, `-gpu host`)는 모두 `Failed to find ColorBuffer` 후 멈췄다. `-no-window -no-audio -gpu swiftshader_indirect -feature -Vulkan` 으로는 부팅됐다. 이 모드는 소리를 낼 수 없어서 음성 출력 확인은 여전히 못 했다.

| 항목 | 결과 |
|-|-|
| T001 스파이크 | `ai.pleos.playground.caas` 패키지와 `pleos.car.permission.TTS_SERVICE` 권한이 있다. 설치만으로 `granted=true`(일반 권한, `pm grant` 는 "not a changeable permission type") |
| T032 첫 실행 | 앱이 종료되지 않고 열린다. 로그 `voice availability=UNKNOWN` → `voice=READY` (약 2초) |
| 이벤트 → 말하기 | 기어 P에서 `TRIP_START bubble=true`, D단(제한 있음, `bits=0x50`)에서 `HARD_ACCEL bubble=false` 가 로그에 찍힘. 말하기 시작과 함께 포커스 요청(granted=true), 선점 시 반납 후 재요청 |
| 제한 중 화면 | 스크린샷에서 말풍선 없음, 정적 자세, 음성 스위치 비활성 모양. `gfxinfo` Total frames rendered: 0 |
| 제한 중 CPU | `top` 두 번 측정 1.5%, 5.3% (5% 기준 근처, 표본이 적어 `[확인 필요]`) |
| 커버 20회 포커스 짝(시나리오 15) | 미확인. 커버 동안 이벤트가 만들어지지 않아 요청 0, 반납 0이라 의미 없음(5회 재시도도 같음) |
| 미확인 | 소리, 선행 무음 효과, 포커스 4가지 실제 변경(시나리오 9~10), 시나리오 11~14, 16~17, 가로/세로 |

### 2차 시도 (2026-11-14, 헤드리스 재부팅 후)

- 실차 모드 P→D 전환에서 `TRIP_START bubble=true`가 로그에 찍힌다(두 번 재현). 다만 말풍선 글자가 화면에 그려졌는지는 못 봤다. `screencap`/`uiautomator dump`가 1~10초 걸려 4초짜리 말풍선이 사라진 뒤에야 찍혔다.
- 데모 모드 재생에서 이벤트 7종(`TRIP_START`, `SEATBELT_OK`, `HARD_CORNER`, `GOOD_LANE_CHANGE`, `LANE_DEPARTURE`, `HARD_BRAKE`, `TRIP_END`)이 모두 `bubble=false tier=STATIC`으로 말했다(로그에 tier를 더해 확인). 원인은 시나리오다: `DemoScenarios.demoCommute`가 8초에 출발과 함께 `ACTIVE`를 걸고 52초에 `NONE`으로 푼다. 이벤트가 전부 제한 구간 안이라 규칙대로 글자가 없다. 코디네이터 버그가 아니다. 앞서 본 "풀 모션" 표시는 재생 전에 수동으로 넣은 `제한 없음`이 남아 있던 화면이었다(재생을 시작하면 `newSession`이 수동 제한을 지운다).
- 실차 모드 P→D에서도 `TRIP_START` 직후 약 0.4초 뒤 `ACTIVE`가 들어와 `FULL→STATIC`으로 말풍선이 바로 지워진다(규칙 6, 설계대로). 따라서 말풍선 글자가 눈에 보이려면 정차 중 이벤트(`SEATBELT_OK`, 정차 중 `TRIP_END`)가 필요한데, 에뮬레이터에서 이를 만드는 방법을 못 찾았다. 말풍선이 실제로 그려지는 것은 **미확인**이다.
- 실차 모드에서는 제한 없음 상태가 정차 중에만 생겨서 `HARD_*` 이벤트는 항상 `bubble=false`다(설계대로). 말풍선이 보이는 이벤트는 `TRIP_START`, `SEATBELT_OK`, `TRIP_END`처럼 정차 중 이벤트뿐이다.

### 디버그 "말하기" 버튼으로 말풍선 확인 (2026-11-14)

디버그 컨트롤(데모 모드)에 "말하기" 버튼을 더했다. 누를 때마다 이벤트 10종을 차례로 `SpeechCoordinator.debugSpeak` 로 넣는다. 현재 `output.tier` 와 쿨다운을 그대로 쓰므로 말풍선과 제한 중 규칙은 실제 이벤트와 같다. 실차 모드와 화면에 보이지 않을 때는 아무것도 하지 않는다(릴리즈 빌드에서는 컨트롤 전체가 안 그려진다).

| 항목 | 결과 |
|-|-|
| 시나리오 1 (제한 없음에서 말풍선) | 확인. `제한 없음` 후 "말하기" → 로그 `TRIP_START bubble=true tier=FULL`, 화면 상단 중앙에 "출발해요. 오늘도 안전하게 가요." 말풍선 |
| 시나리오 6 (말풍선 중 제한) | 확인. 말풍선이 떠 있을 때 `제한 있음` → 0.5초 안에 말풍선이 사라지고 정적 자세 |
| 시나리오 5 (제한 중 이벤트) | 확인. `HARD_BRAKE bubble=false tier=STATIC`, 화면에 글자 없음 |
| 말풍선 위치 | 상단 중앙, 펫과 컨트롤 패널을 가리지 않음. 글자가 작아 보이는 것은 2560 px 스크린샷 축소 때문(14 sp, 실측은 `[확인 필요]`) |
| 단위 테스트 | `SpeechCoordinatorTest` 에 `debugSpeakFollowsCurrentTier`, `debugSpeakIgnoredInBackground` 추가, 통과 |

### 시나리오 12~14 (2026-11-14, 헤드리스 에뮬레이터, "말하기" 버튼)

| 항목 | 결과 |
|-|-|
| 시나리오 12 (연속 이벤트) | 부분 확인. 1초 안에 4번 누르면 `TRIP_START` → (중지, 반납) → `SEATBELT_OK` → `HARD_ACCEL` 순으로 말하기 시작이 찍힌다. 마지막 `HARD_BRAKE` 는 말하지 않았다(쿨다운 또는 대기 만료로 보이나 로그에 이유가 없어 **확인 필요**). |
| 시나리오 12 발견 1 | 선점 직후 포커스 재요청이 `granted=false` 로 끝나 `speech failed reason=NO_FOCUS`. 시스템 로그(`CarAudioFocus`)는 같은 요청을 `REQUEST_GRANTED` 로 평가했다. 반납 직후 같은 리스너로 다시 요청하는 경합으로 의심하나 원인은 **확인 필요**. 실차에서 위험 경고가 이 경합에 걸리면 말이 빠질 수 있다. |
| 시나리오 12 발견 2 | 말하기가 시작돼도 에뮬레이터의 TTS 서비스가 끝 콜백을 주지 않아 감시 시간(8초)으로 끝난다(`watchdog timeout` → 포커스 반납 → `reason=TIMEOUT`). 감시 시간 경로는 의도대로 동작. 실기기에서 끝 콜백이 오는지는 **확인 필요**. |
| 시나리오 13 (TTS 없음, 이벤트 100개) | 확인. `pm disable-user ai.pleos.playground.caas` 후 `voice=UNAVAILABLE reason=initialize failed`. "말하기" 100번 → 22번 시도, 22번 모두 `speech failed reason=ENGINE_ERROR`, 말풍선 정상, 크래시와 ANR 없음, 같은 프로세스. 끝나고 `pm enable` 로 되돌림. |
| 시나리오 14 (음성 끄기) | 확인. 끄면 `silent event=... reason=voice-off` 로 소리 없이 말풍선만. 앱을 껐다 켜도 꺼진 상태 유지. 제한 있음에서 스위치를 눌러도 상태가 안 바뀌고, 제한을 풀어도 그대로. |
| 시나리오 15, 16 | **미확인**. 에뮬레이터 부하(load 10~20)로 탭이 반영되지 않았다. HOME 키는 멀티 윈도우 때문에 앱을 가리지 못한다(`Activity not started ... top-most instance`). |

환경 메모: 재부팅 직후나 `caas` 를 켜고 끈 직후 몇 분간 load 가 10~20 으로 올라가고, 이때 `link=CONNECTING` 에서 앱이 멈추거나 ANR(`Waited 5001ms for FocusEvent`)이 났다(04:14, 04:19). 04:14 건은 TTS 를 끄기 전이라 TTS 와 무관하다. 부하가 3 이하일 때는 같은 조건에서 정상이었다. 다만 차량 서비스가 느릴 때 앱 메인 스레드가 막히는지는 **확인 필요**(연결을 메인에서 기다리는 코드가 있는지 점검 대상).

## 시나리오 12 재확인: 포커스 재요청 수정 후 (2026-11-15, 헤드리스 에뮬레이터)

- 변경: `AndroidAudioFocusGateway` 가 요청마다 새 리스너를 쓰고, 거절 시 시스템이 돌려준 값을 `focus request result=N` 으로 남긴다.
- 방법: 데모 모드, 제한 없음, 말하기 버튼을 0.3~0.4초 간격으로 4~5번씩 3회 눌렀다.
- 결과: 반납 직후 재요청 5번이 모두 `granted=true`. `NO_FOCUS` 와 `focus request result=` 는 나오지 않았다. 수정 전에는 같은 조작에서 `granted=false` → `NO_FOCUS` 가 났다.
- 한계: 수정 전 거절의 원인은 재현하지 못해 확정하지 않았다(가설: 같은 리스너로 반납 직후 재요청). 간헐 현상이었다면 3회로는 배제할 수 없다. [확인 필요]
- 관찰: 연속으로 누른 4번째 이후 이벤트는 말하지 않고 로그도 없다(쿨다운 또는 규칙에 의한 건너뜀으로 보이나 이유가 로그에 없다). [확인 필요]
- 관찰: 이번에는 마지막 말이 3~5초 만에 끝나고(`focus abandon`), `watchdog timeout` 은 없었다. 에뮬레이터 TTS 의 끝 콜백이 오는지는 일관되지 않아 실기기에서 확인한다. [확인 필요]
- 환경: 내비 앱이 CPU 를 계속 써서 부하가 12 안팎이었지만 이번 조작은 정상 반영됐다.

## 시나리오 15, 16 (2026-11-15, 헤드리스 에뮬레이터, 부하 12 안팎)

- 시나리오 15 (가렸다 열기 ×20): 통과. 데모 모드, 제한 없음에서 말하기를 누른 뒤 `am start --user 10 -a android.settings.SETTINGS` 로 덮고(앱 태스크 `visible=false` 확인) `am start -n com.example.dashpet/.MainActivity` 로 되돌리는 것을 20번 반복했다. `Speech` 로그: `focus request granted=true` 20, `focus abandon` 20, `speak event` 20, `speech failed` 0, `watchdog` 0. 요청 후 반납까지 2.3~4.6초로, 8초 감시가 아니라 가려서 멈춘 것이다. 크래시 없음.
- 시나리오 16 (모드 전환 ×10): 통과. 데모에서 말하기 → 실차 → 데모 → 제한 없음을 10번 반복했다. 요청 10, 반납 10(요청 후 1.3~2.0초, 전환 시점), 크래시와 ANR 없음, 프로세스 id 유지. 실차 모드 화면(5번째)에서 말풍선이 남아 있지 않았다. 전환 후 대기 중이던 음성이 이어 말한 로그도 없다.
- 한계: 소리는 확인하지 못했다(`-no-audio`). 말풍선이 없음은 5번째 전환에서만 화면으로 확인했고 나머지는 로그로만 봤다. 가리기는 같은 창 영역에 설정 앱을 띄우는 방식이다(HOME 키와 `--windowingMode 1` 은 이 환경에서 앱을 가리지 못했다).

## 시나리오 17 (2026-11-15, 헤드리스 에뮬레이터, 부하 10 안팎): 일부만 확인

- 기준: 2560x1440, 밀도 160(오버라이드 없음). 끝나고 `wm size reset`, `wm density reset` 로 되돌렸다(둘 다 오버라이드 없음 확인).
- `wm density 360`(말하는 중에 변경): 음성이 끊기지 않았고(변경 후 포커스 반납은 요청 5.3초 뒤, 중간 중지 로그 없음) 프로세스 id 유지, 크래시 없음. 이 밀도에서 다시 말하게 했을 때 말풍선 문장 "안전벨트를 확인했어요."가 잘리지 않고 전부 보였다. [확인 완료]
- 같은 밀도에서 확인용 컨트롤 패널이 커져 펫이 거의 가려지고 패널 아랫부분(패널 숨기기 버튼)이 잘렸다. 디버그 빌드 전용 패널의 문제로 보이며 release 빌드에는 해당하지 않는다. [확인 필요: release 에서는 패널이 없으므로 펫과 말풍선만 다시 확인]
- `wm size 1600x900`(밀도 360, 말하는 중에 변경): 음성은 끊기지 않았고(요청 3.9초 뒤 반납) 프로세스 id 유지, 크래시와 ANR 로그 없음. 그러나 앱 창이 배경색만 보이고 펫과 패널이 그려지지 않았다. 밀도만 되돌려 1600x900, 밀도 160 으로 두어도 같았고, 크기를 되돌리자 정상으로 돌아왔다. 말풍선은 이 크기에서 확인하지 못했다. 에뮬레이터 부하로 프레임 97% 가 늦은 상태였고, 앱 문제인지 재배치가 늦은 것인지 구분하지 못했다. [확인 필요]
- 되돌린 직후 화면에서 앱 위쪽에 빈 띠가 보이고 패널 아랫부분이 잘려 있었다. 재배치가 끝나기 전이었는지 확인하지 못했다. [확인 필요]
