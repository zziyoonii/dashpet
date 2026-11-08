# Quickstart: PetView 검증

이 기능이 끝났는지 확인하는 방법이다. 구현 코드는 `tasks.md` 와 구현 단계에서 쓴다.

## 준비

- 시스템에 JDK 가 없으므로 Android Studio 내장 JBR 을 쓴다.
  ```bash
  export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
  ```
- 화면 확인에는 Pleos Connect 에뮬레이터(API 34, Cold boot, RAM 4GB)가 필요하다. 설치 여부와 GPU 성능은 아직 확인하지 않았다(`[확인 필요]`).

## 1. 자동 테스트 (에뮬레이터 불필요)

```bash
./gradlew :signal:testDebugUnitTest :engine:testDebugUnitTest :pet:testDebugUnitTest :app:testDebugUnitTest --console=plain
```

기대 결과: `BUILD SUCCESSFUL`, 실패 0건. 001 기능 테스트(145개)도 함께 통과해야 한다(회귀 확인).

| # | 확인할 것 | 관련 테스트 | 스펙 |
|-|-|-|-|
| 1 | 소프트바디: 충격 뒤 출렁임이 감쇠하고 변위가 −0.32~0.42 를 넘지 않는다. 모션 줄이기에서 더 빨리 가라앉는다 | `SoftBodyTest` | FR-004, FR-015 |
| 2 | 급커브 반응: 방향별로 반대편 벽으로 쏠리고 지속 시간이 `0.5 + 0.8·s` 초, 세기에 비례한다. 급제동은 유리 접촉으로 `press` 가 오르고, 급가속은 `z` 가 등 뒤로 간다 | `PetPhysicsTest` | FR-003, FR-004, US1 |
| 3 | 점프: 활발이 듬직보다 높다. 착지 충격이 스케일에 반영된다 | `PetPhysicsTest` | FR-011 |
| 4 | 정적 모드에서 `onEvent`, 감정 진입 효과, `step` 이 아무 상태도 바꾸지 않는다. `needsFrames == false` | `PetControllerTest` | FR-006, SC-002 |
| 5 | 등급이 풀 → 정적이 되면 같은 호출 안에서 물리와 파티클이 초기화된다. 정적 → 풀은 지난 이벤트를 재현하지 않는다 | `PetControllerTest` | FR-009, SC-003, US3 |
| 6 | 일회성 등급과 알 수 없는 등급은 정적으로 처리된다 | `PetControllerTest` | FR-008 |
| 7 | 이벤트 방향 매핑(`RIGHT` → 오른쪽 패널, `LEFT`, `FRONT`, `BACK`)과 세기 전달 | `PetControllerTest` | R3, US1 |
| 8 | 같은 시드와 같은 입력 열이면 `renderState` 가 같다 | `PetControllerTest` | SC-009 |
| 9 | 정적 포즈: 감정별 색과 찌그러짐 방향별 기울기, 방향지시등 눈 감김과 호박색 눈썹, 비의 우비 | `PetPoseTest` | FR-005, FR-010, FR-019 |
| 10 | 루프 실행 조건: 풀 모션 + `STARTED` 일 때만 실행, 정적이나 백그라운드는 중지. 재개 첫 `dt = 0` | `MotionPolicyTest` | FR-006, FR-013, SC-007 |
| 11 | 파티클 상한 60개, 프레임이 느리면 생성 중단 후 회복 시 재개, 정적과 모션 줄이기에서는 생성 안 함 | `ParticlesTest` | FR-014, FR-015 |
| 12 | 수동 제한 상태 주입이 엔진 출력의 등급을 바꾼다. 재생 중이 아니어도 동작한다 | `DrivingEngineTest`(보강) | FR-018, US6 |
| 13 | `Tick` 이 이벤트 없이 시간만 진행하고 감정이 지속 시간 뒤 편안으로 돌아간다. 데모를 `Tick` 포함으로 재생하면 23초 급커브 뒤 감정이 1.2초 뒤 만료된다 | `EngineCoreTickTest`, `MockSignalSourceTest`(보강) | R12 |
| 14 | `DemoViewModel`: 재생/정지/재생 끝 정지, 수동 제한 주입, 디버그 불가 빌드에서 무시 | `DemoViewModelTest` | FR-017, FR-018 |

## 2. 에뮬레이터 확인

```bash
./gradlew :app:installDebug
adb shell am start -n com.example.dashpet/.MainActivity
```

| # | 확인 방법 | 기대 결과 | 스펙 |
|-|-|-|-|
| A | 앱 홈을 열고 아무것도 하지 않는다 | 펫이 숨쉬듯 움직이고 눈을 2.6~5.2초마다 깜빡인다(풀 모션, 제한 상태가 없을 때) | US1-1 |
| B | 데모 재생을 누르고 8초 이후를 본다 | 제한이 걸리는 8초부터는 펫이 정적 포즈와 색으로만 바뀐다. 22초 급커브는 왼쪽으로 기울어진 포즈, 38초 차선 이탈은 주황, 46초 급제동은 앞 방향 정적 포즈(1.08배). 각 표정은 지속 시간 뒤(급커브 1.2초) 편안으로 돌아간다(`Tick` 필요) | US2, SC-006 |
| C | 재생 중 제한 상태를 "없음"으로 바꾼다 | 곧바로 풀 모션이 된다. "있음" 또는 "알 수 없음"으로 바꾸면 다음 갱신부터 모션이 멈춘다 | US3, SC-003 |
| D | 정적 상태에서 10초 동안 둔다 | `adb shell dumpsys gfxinfo com.example.dashpet reset` 후 10초 뒤 `dumpsys gfxinfo com.example.dashpet` 의 `Total frames rendered` 가 0 또는 매우 작다 | SC-002 |
| E | 풀 모션에서 `reset` 후 10초 동안 `dumpsys gfxinfo` | 평균 프레임 시간이 약 16.6ms(60fps), `Janky frames` 비율이 낮다. 55fps 미만이면 수치와 줄인 효과를 기록 | SC-005 |
| F | 홈 버튼으로 앱을 백그라운드로 보내고 1초 뒤 `gfxinfo reset` 후 5초 | 프레임이 0 에 가깝다 | SC-007 |
| G | 화면을 회전하거나 분할 크기를 바꾼다 | 감정, 외형, 재생 위치가 유지되고 펫이 잘리지 않는다 | SC-008 |
| H | 데모를 3번 재생한다 | 매번 같은 이벤트 순서로 같은 반응이 나온다 | SC-009 |
| I | 시스템 설정에서 애니메이션을 끈 뒤 풀 모션에서 급제동 | 출렁임이 빨리 가라앉고 파티클이 없다 | FR-015 |
| J | 앱을 백그라운드로 갔다 돌아온다 | 현재 상태를 즉시 그리고 물리가 튀지 않는다 | FR-013 |

## 3. 시안과 나란히 비교

`checklists/visual-parity.md` 를 열고, 캐릭터 시안(`docs/DashPet 캐릭터 시안.html`)과 에뮬레이터를 나란히 놓고 항목을 확인한다. 기획자 승인이 SC-001 이다.

## 완료 기준

- 위 1번이 모두 자동 테스트로 존재하고 통과한다(001 회귀 포함).
- 2번 A~J 를 에뮬레이터에서 확인하고 결과(특히 D, E 의 수치)를 기록했다. 에뮬레이터가 없으면 확인하지 못한 항목을 명시한다.
- 3번 체크리스트에 기획자 승인이 있다.
- 정적 등급에서 프레임 루프, 애니메이터, 타이머가 없다(코드 점검).
- `docs/` 와 Pleos 문서 원문은 커밋하지 않았다.
