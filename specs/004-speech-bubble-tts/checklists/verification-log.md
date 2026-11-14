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
