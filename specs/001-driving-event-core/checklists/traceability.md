# Traceability: 요구사항 -> 테스트

FR-001~FR-024 와 SC-001~SC-006 을 대응하는 테스트로 연결한다. 테스트 이름은 `클래스.메서드` 이다.
`engine` 테스트는 `engine/src/test/.../engine/`, `signal` 테스트는 `signal/src/test/.../signal/` 아래에 있다.

## Functional Requirements

| FR | 내용 | 대응 테스트 |
|-|-|-|
| FR-001 | 10가지 이벤트 | `ValueTypesTest.eventTypesAreTheTenFromFr001`, `EngineCoreMotionTest.fullMotionNeverAppearsWhileRestrictedOrUnknown`(10종 모두 발생 확인), `DemoScenarioTest.demoCommuteProducesSevenEventsInOrderAndScore94` |
| FR-002 | 급가속·급제동·급커브 임계값, 페달 조건, 15km/h 조건, 한 곳에서 변경 | `AccelEventDetectorTest.hardBrakeFrom50To0In2SecondsWithPedal`, `sameDecelerationWithoutPedalIsNotHardBrake`, `rightTurnIsHardCornerLeaning_LEFT`, `largeHeadingChangeBelow15KmhIsNotHardCorner`, `thresholdsComeFromEngineConfig`, `ValueTypesTest.engineConfigDefaultsMatchDataModelTable` |
| FR-003 | 가속도 계산식 | `KinematicsTest.longitudinalAccelIsSpeedChangeRate`, `lateralAccelIsYawRateTimesSpeed`, `angleDiffWrapsAroundNorth` |
| FR-004 | 평활화와 2회 연속 확정 | `KinematicsTest.speedIsSmoothedWithEma`, `headingIsUnwrappedBeforeSmoothing`, `AccelEventDetectorTest.singleSampleOverThresholdIsNotConfirmed`, `belowThresholdSampleResetsConsecutiveCount` |
| FR-005 | 간격 2초 초과, 속도 없는 샘플 버림 | `KinematicsTest.speedGapOverLimitDropsSampleAndResetsBaseline`, `gapExactlyAtLimitIsStillValid`, `headingGapOverLimitResets`, `headingWithoutSpeedInfoIsDroppedAndBaselineReset` |
| FR-006 | 세기(0~1)와 방향 | `AccelEventDetectorTest.intensityIsNormalizedBetweenThresholdAndMax`, `intensityIsClampedToOne`, `hardAccelIntensityUsesAccelThreshold`, `leftTurnIsHardCornerLeaning_RIGHT`, `EngineCoreAccelTest.hardBrakeScenario`, `hardAccelScenario`, `ValueTypesTest.drivingEventIntensityMustBeInUnitRange` |
| FR-007 | 재발 간격(3/10/5초), 주행당 1회 | `AccelEventDetectorTest.sameKindWithinCooldownIsRecognizedOnce`, `WarningEventDetectorTest.repeatedWithin10SecondsIsRecognizedOnce`, `collisionAndProximityShareTheSameInterval`, `LaneChangeEventDetectorTest.evaluationsWithin5SecondsAreRecognizedOnce`, `TripTrackerTest.seatbeltOkOnlyWhenWarningTurnsOnAndOffAfterDeparture`(벨트 1회), `TripTrackerTest.secondTripGetsNewIdAndFreshScore`(출발/도착은 주행마다 1회) |
| FR-008 | 직전 3초 안 같은 방향 방향지시등 | `LaneChangeEventDetectorTest.sameDirectionSignalWithinLookbackIsGood`, `oppositeDirectionSignalIsNoSignalLaneChange`, `signalTurnedOffJustBeforeLaneChangeCountsWithinLookback`, `signalTurnedOffRightAfterLaneChangeStillCounts` |
| FR-009 | 감정 우선순위와 이벤트->감정 표 | `ValueTypesTest.moodPrioritiesFollowFr009`, `MoodMachineTest.eventToMoodTable`, `tripEndIsHappyOnlyWhenScoreAtLeast80`, `EngineCoreTripTest.tripEndWithScoreAtLeast80IsHappy`, `tripEndWithScoreBelow80IsNotHappy` |
| FR-010 | 감정 지속 시간과 복귀 | `MoodMachineTest.durationsFollowConfig`, `laneDepartureMakesDizzyThenIdleAfter3Seconds`, `returnsToBackgroundStateWhenPresent`, `EngineCoreMoodTest.laneDepartureMakesDizzyThenReturnsAfter3Seconds` |
| FR-011 | 같거나 높으면 전환, 낮으면 버림, 대기열 없음 | `MoodMachineTest.higherPriorityInterruptsImmediately`, `lowerPriorityIsDroppedAndNotQueued`, `samePriorityTransitionsAndRestartsDuration`, `EngineCoreMoodTest.droppedReactionIsNotQueued`, `lowerPriorityEventDuringStartledIsDroppedFromMoodButStillReported` |
| FR-012 | 깜빡이 없는 차선 변경은 감정 불변 | `MoodMachineTest.eventsWithoutMoodLeaveStateUnchanged`, `EngineCoreTripTest.laneChangeWithoutSignalKeepsMoodAndOnlyRecordsAndScores`, `noSignalLaneChangeDoesNotInterruptCurrentMood` |
| FR-013 | 모든 이벤트를 주행 기록에 남김 | `TripTrackerTest.tripRecordsAllEventsWithScoreAndIsCompleted`, `EngineCoreTripTest.currentAndCompletedTrips`, `laneChangeWithoutSignalKeepsMoodAndOnlyRecordsAndScores` |
| FR-014 | 점수 가감, 가점 상한, 마지막에만 자름 | `ScoreKeeperTest.*`(`demoCommuteSumsTo94`, `weightsTable`, `goodLaneChangeBonusIsCappedAtFivePerTrip`, `capMattersBelow100`, `clampsOnlyWhenQueried_high`, `clampsOnlyWhenQueried_low`) |
| FR-015 | 제한 상태 -> 모션 등급 | `MotionGateTest.noRestrictionAllowsFullMotion`, `activeRestrictionIsStatic`, `unknownRestrictionIsStatic`, `EngineCoreMotionTest.initialRestrictionIsUnknownSoOutputIsStatic` |
| FR-016 | 일회성 전환은 설정으로만, 기본 꺼짐 | `MotionGateTest.oneShotIsNeverOutputByDefault`, `oneShotIsPossibleOnlyWhenEnabledAndRestrictionActive`, `EngineCoreMotionTest.oneShotIsNotOutputWithDefaultConfigEvenWhenRestricted`, `oneShotAppearsOnlyOnEventStepsWhenEnabledAndActive` |
| FR-017 | 제한 변화는 다음 출력부터 반영 | `EngineCoreMotionTest.restrictionSignalChangesOutputEvenWithoutEvents`, `fullMotionBecomesStaticFromNextOutputWhenRestricted` |
| FR-018 | 감정, 강도, 방향, 등급, 방향지시등 출력 | `EngineCoreMoodTest.outputCarriesEventIntensityAndDirection`, `DrivingEngineTest.outputIsUpdatedOnlyWhenValueChanges`, `outputStateFlowReplaysLastValueToLateSubscribers`, `ValueTypesTest.sameStateIgnoresOnlyTimestamp` |
| FR-019 | 방향지시등 상태를 이벤트와 별개로 전달 | `EngineCoreTurnSignalTest.*` |
| FR-020 | 시나리오 재생, 10Hz 풀기 | `ScenarioExpandTest.speedRampExpandsAt100msIncludingBothEnds`, `headingTurnIsRelativeToPreviousHeading`, `MockSignalSourceTest.*`, `RawSignalTest.*` |
| FR-021 | 같은 시나리오 -> 같은 결과 | `DemoScenarioTest.hundredRunsAreIdentical`, `ScenarioExpandTest.expandIsRepeatable`, `MockSignalSourceTest.sameScenarioGivesSameStream` |
| FR-022 | 판정 규칙이 신호 출처를 모름 | `DrivingEngineTest.worksWithAnyVehicleSignalSource`(MockSignalSource 가 아닌 소스), 헌장 점검 grep(tasks.md T051) |
| FR-023 | 설정을 한 곳에 모으고 테스트에서 변경 | `AccelEventDetectorTest.thresholdsComeFromEngineConfig`, `MoodMachineTest.durationsFollowConfig`, `ScoreKeeperTest.weightsAndCapComeFromConfig`, `ValueTypesTest.engineConfigDefaultsMatchDataModelTable` |
| FR-024 | 출발/도착은 주차 상태 변화로 판정 | `TripTrackerTest.parkedToDrivingProducesTripStart`, `firstInputDrivingStartsTripWithoutEvents`, `arrivalWaitsUntilSpeedIsZero`, `arrivalIsImmediateWhenAlreadyStopped`, `arrivalWithUnknownSpeedWaitsForSpeed`, `drivingAgainWhileArrivingCancelsArrivalWithoutNewTrip`, `EngineCoreTripTest.parkedWhileMovingArrivesOnlyWhenSpeedReachesZero` |

## Success Criteria

| SC | 내용 | 대응 테스트 |
|-|-|-|
| SC-001 | 데모 시나리오 이벤트 7개, 94점 | `DemoScenarioTest.demoCommuteProducesSevenEventsInOrderAndScore94`, `scoreIsExactly94`, `eventTimesMatchTheStory` |
| SC-002 | 100번 반복해도 같음 | `DemoScenarioTest.hundredRunsAreIdentical`, `moodTransitionsFollowTheStory` |
| SC-003 | 제한 중 풀 모션 0건 | `EngineCoreMotionTest.fullMotionNeverAppearsWhileRestrictedOrUnknown`(10종 x ACTIVE/UNKNOWN/신호 없음), `DemoScenarioTest.noFullMotionWhileDriving`, `MotionGateTest.*` |
| SC-004 | 완만한 가속과 15km/h 미만 방향 변화는 오판정 없음 | `AccelEventDetectorTest.gentleAccelerationOver8SecondsIsNotHardAccel`, `largeHeadingChangeBelow15KmhIsNotHardCorner`, `EngineCoreAccelTest.gentleAccelerationAndSlowTurnProduceNoEvents`, `DemoScenarioTest.noHardAccelDuringGentleAcceleration` |
| SC-005 | 모든 규칙에 자동 테스트, 10초 안에 전체 실행 | 위 표 전체. 실행 시간은 tasks.md T052 결과 참고 |
| SC-006 | 출처를 바꿔도 판정 규칙 불변 | `DrivingEngineTest.worksWithAnyVehicleSignalSource`, `eventsFlowMatchesCoreResults`(Mock 소스)와 같은 결과 |
