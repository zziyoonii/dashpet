# Traceability: 요구사항 -> 테스트와 확인

FR-001~FR-021 과 SC-001~SC-009 를 대응하는 테스트(`클래스.메서드`), `visual-parity.md` 항목, `quickstart.md` 2번의 에뮬레이터 확인 항목(A~J)으로 연결한다.
`pet` 테스트는 `pet/src/test/.../pet/`, `engine` 테스트는 `engine/src/test/.../engine/` 아래에 있다. 에뮬레이터 결과는 `verification-log.md` 에 있다.
**"테스트 없음"은 자동 테스트로 확인할 수 없는 항목이다**(그리기 결과, 시스템 동작). 해당 항목은 에뮬레이터 확인이나 기획자 승인에 의존한다.

## Functional Requirements

| FR | 내용 | 자동 테스트 | 에뮬레이터 / 승인 |
|-|-|-|-|
| FR-001 | 엔진 출력, 외형, 표시 모드 입력 | `PetControllerEngineTest.demoCornerAndBrakeSurgeAndMoodOrderMatchesEngine`, `PetControllerStaticTest.directionAndTurnSignalChangeThePose`, `appearanceChangeUpdatesPoseColor` | A, 초기 |
| FR-002 | 감정 5종 표현 | 테스트 없음(그리기) | `visual-parity.md` A, 에뮬레이터 A, B(장면별 결과) |
| FR-003 | 방향별 물리 반응 | `PetPhysicsTest.cornerRightPushesTowardRightWall`, `hardBrakeReachesGlassAndPresses`, `accelPushesBackAndLeans`, `jumpFallsUnderGravityAndLandingSquashesScale`, `PetControllerFullTest.hardCornerRightIsPlusOne`, `hardCornerLeftIsMinusOne`, `hardBrakeFrontIsSurgePlusOneAndAccelBackIsMinusOne` | B(급제동 창의 약 90%) |
| FR-004 | 강도 비례, 유지 후 출렁이며 복귀 | `PetPhysicsTest.cornerForceAndDurationFollowStrength`, `strongerCornerPenetratesMoreAndLastsLonger`, `cornerReleaseKicksBackAgainstTheWall`, `strengthIsClampedToZeroOne` | B |
| FR-005 | 정적 포즈와 색 | `PetPoseTest.happyMixesGreenAt0_9`, `dizzyMixesAmberAt0_8`, `startledMixesRedAt0_85WithoutBlink`, `squashedLeansTowardDirection`, `idleKeepsBodyColorAndUprightShape` | 정적 장면 결과, `visual-parity.md` C |
| FR-006 | 정적에서 모션, 파티클, 갱신 없음 | `PetControllerStaticTest.stepDoesNothingInStatic`, `moodEntryEffectsAreIgnoredInStatic`, `sameOutputDoesNotInvalidate`, `ParticlesAmbientTest.staticModeMakesNoParticles` | D(프레임 0) |
| FR-007 | 정적에서 터치 무반응 | 테스트 없음. `pet/src/main` 에 터치 처리(`pointerInput`, `clickable`)가 없음(코드 검색, T071) | 미확인(에뮬레이터 터치) |
| FR-008 | ONE_SHOT, 알 수 없는 등급은 정적 | `PetControllerStaticTest.oneShotBehavesLikeStatic` | |
| FR-009 | 등급 전환 즉시 반영, 지난 이벤트 재현 없음 | `PetControllerTransitionTest.fullToStaticClearsEverythingInTheSameCall`, `staticToFullStartsFreshPhysics`, `rejumpScheduleDoesNotSurviveStaticDetour`, `PetControllerFullTest.enteringFullWhileAlreadyHappyDoesNotReplayEntryEffect` | C |
| FR-010 | 방향지시등 윙크(풀 380ms, 정적 선) | `PetControllerWinkTest.rightSignalClosesRightEyeFirstThenAlternatesEvery380ms`, `leftSignalWinksLeftEyeOnly`, `turningOffRemovesWinkImmediately`, `winkStaysWithReducedMotion`, `staticModeHasNoTimedWink`, `PetPoseTest.turnSignalClosesSameSideEyeAndShowsAmberBrow` | 에뮬레이터(2026-11-15, 윙크 우: 감김/뜸 확인) |
| FR-011 | 몸 색 그라데이션, 애교 볼터치, 활발 점프 | `PetControllerAppearanceTest.livelyJumpsHigherThanSturdyOnHappy`, `cuteAlwaysHasCheeksWhateverTheMood`, `otherPersonalitiesHaveCheeksOnlyWhenHappy`, `PetPaletteTest.outlineAlphaBoundaryAt0_3`, `mixIsSrgbLinearAndRounded` | `visual-parity.md` 외형 |
| FR-012 | 크기 비례, 회전과 분할에서 유지 | 테스트 없음 | G 미확인 |
| FR-013 | 백그라운드에서 갱신과 시간 정지, 복귀 시 즉시 | `MotionPolicyTest.loopRunsOnlyForFullMotionWhileStarted`, `firstFrameAfterStartOrResumeHasZeroDt` | F, J 미확인 |
| FR-014 | 60fps 목표, 못 지키면 파티클부터 줄임 | `ParticlesTest.throttlesAfterThirtyConsecutiveSlowFramesAndResumesOnRecovery`, `controllerStopsSpawningWhenFramesAreSlowAndResumes`, `ParticlesAmbientTest.stopsWhileThrottledForSlowFrames` | E(약 58fps, 경계) |
| FR-015 | 모션 줄이기 | `PetPhysicsTest.reducedMotionDampsHarder`, `SoftBodyTest.reducedMotionSettlesFasterAndKicksSmaller`, `MotionPolicyTest.reducedMotionCorrection`, `PetControllerFullTest.reducedMotionSkipsJitter`, `ParticlesAmbientTest.reducedMotionMakesNoParticles` | I 미확인 |
| FR-016 | 텍스트 대비 4.5:1 이상, 12sp 이상 | `PetPaletteTest.nameLabelContrastIsAtLeast4_5AgainstEveryBackground`, `contrastRatioMatchesWcag` | 에뮬레이터(이름 라벨 14sp 표시 확인) |
| FR-017 | 데모 재생, 정지, 다시 재생 | `DemoViewModelTest.initialStateIsStopped`, `togglePlayStartsThenStops`, `playbackReachesTheEndAndStops`, `immediatePacingProducesTheDemoEvents`, `stopReturnsPetToCalmAndResetsSession`, `newSessionClearsManualRestrictionAndRestartsFromBeginning`(app) | B |
| FR-018 | 수동 제한 토글, 확인용 표시 | `DemoViewModelTest.restrictionChangesTierEvenWhenNotPlaying`, `restrictionIgnoredWhenDebugUnavailable`, `debugPanelVisibilityToggles`, `timeLabelFormat`(app) | C |
| FR-019 | 배경 감정 4종 | `PetControllerAmbientTest.*`, `ParticlesAmbientTest.*`, `PetPoseTest.hotColdQuietHaveNoSeparateStaticLook`, `hoodShowsOnlyWhileTheEngineMoodIsIdle` | 에뮬레이터(2026-11-15, 4종 모두 확인). 단 앱에는 이 값을 넣는 입력이 아직 없어 확인용 임시 코드로 확인 |
| FR-020 | 시안과 나란히 비교, 기획자 승인 | 테스트 없음 | `visual-parity.md`. **기획자 승인 미완(T074)** |
| FR-021 | 외형 값이 없으면 기본 외형, 예외 없음 | `PetAppearanceTest.defaultsAreUsedWhenNoValueIsGiven`, `emptyOrBlankNameFallsBackToDefaultDisplayName`, `anyInputBuildsWithoutThrowing`, `PetControllerAppearanceTest.missingAppearanceFallsBackToTheDefault`, `blankNameShowsTheDefaultName` | |

## Success Criteria

| SC | 내용 | 자동 테스트 | 에뮬레이터 / 승인 |
|-|-|-|-|
| SC-001 | 시안과 나란히 확인한 항목이 모두 통과 | 테스트 없음 | `visual-parity.md`. **기획자 승인 미완(T074)** |
| SC-002 | 정적 10초 화면 갱신 0회 | `PetControllerStaticTest.sameOutputDoesNotInvalidate`, `stepDoesNothingInStatic` | D(프레임 0, Janky 0). 통과 |
| SC-003 | 정적 전환 후 1회 안에 모든 움직임 정지 | `PetControllerTransitionTest.fullToStaticClearsEverythingInTheSameCall` | C 부분 확인 |
| SC-004 | 출력이 바뀐 뒤 0.25초 안에 반응 시작 | 테스트 없음(`applyOutput` 은 같은 호출에서 상태를 바꾼다) | 미측정 |
| SC-005 | 풀 모션 평균 55fps 이상 | 테스트 없음 | E: 약 58fps, p99 73 ms(경계). 통과(경계) |
| SC-006 | 제한 구간(8~52초)에 풀 모션 0건 | `PetControllerEngineTest.staticTierFromDemoScenarioIgnoresEvents` | B: 14~62초 캡처 프레임 모두 정적 |
| SC-007 | 백그라운드 1초 안에 갱신 정지 | `MotionPolicyTest.loopRunsOnlyForFullMotionWhileStarted` | F 미확인(액티비티가 stop 되는 것은 로그로 확인) |
| SC-008 | 회전과 분할 크기 변경 후 유지 | 테스트 없음 | G 미확인 |
| SC-009 | 여러 번 재생해도 같은 반응 | `PetControllerFullTest.sameSeedAndSameCallsGiveSameRenderState`, `PetPhysicsTest.sameSeedAndCallsGiveSameState`, `SoftBodyTest.sameInputGivesSameResult` | H 미확인(3회 반복 재생) |

## 빠진 항목 요약

- 자동 테스트가 없는 요구: FR-002, FR-007, FR-012, FR-020, SC-001, SC-004, SC-005, SC-008. 그리기와 시스템 동작이라 JVM 에서 확인할 수 없다.
- 에뮬레이터에서 미확인: 회전과 분할 크기(G, FR-012, SC-008), 백그라운드(F, J, FR-013, SC-007), 데모 3회 반복(H, SC-009), 시스템 애니메이션 끄기(I, FR-015), 정적 터치 무반응(FR-007).
- 사람이 승인해야 하는 항목: 시안과의 대조(FR-020, SC-001)와 정적 포즈 초안 값(`research.md` R8).
