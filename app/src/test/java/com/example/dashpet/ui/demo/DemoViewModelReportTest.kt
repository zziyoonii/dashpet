package com.example.dashpet.ui.demo

import com.example.dashpet.engine.speech.SpeechTone
import com.example.dashpet.pet.Personality
import com.example.dashpet.signal.Pacing
import com.example.dashpet.signal.scenario.DemoScenarios
import com.example.dashpet.ui.profile.MemoryProfileStore
import com.example.dashpet.ui.profile.PetProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 도착 리포트와 프로필(specs/005). */
@OptIn(ExperimentalCoroutinesApi::class)
class DemoViewModelReportTest {
    @Before fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm(store: MemoryProfileStore = MemoryProfileStore()) = DemoViewModel(
        vehicleSource = FakeVehicleSource(),
        scenario = DemoScenarios.demoCommute,
        pacing = Pacing.REAL_TIME,
        debugAvailable = true,
        initialMode = SignalMode.DEMO,
        profileStore = store,
    )

    private suspend fun kotlinx.coroutines.test.TestScope.playToEnd(vm: DemoViewModel) {
        runCurrent()
        vm.togglePlay()
        runCurrent()
        advanceTimeBy(vm.totalMs + 500)
        runCurrent()
    }

    @Test fun noReportBeforeTheTripEnds() = runTest {
        val vm = vm()
        runCurrent()
        assertNull(vm.report.value)
    }

    @Test fun reportAppearsAtTripEndWithScoreAndFirstTripLine() = runTest {
        val store = MemoryProfileStore()
        val vm = vm(store)
        playToEnd(vm)
        val r = assertNotNull(vm.report.value).let { vm.report.value!! }
        assertEquals(94, r.score) // 데모 시나리오의 기대 점수
        assertEquals("첫 주행 기록이에요.", r.deltaLine)
        assertTrue(r.highlights.isNotEmpty())
        assertEquals(94, store.lastScore)
    }

    @Test fun reportComparesWithTheStoredPreviousScore() = runTest {
        val store = MemoryProfileStore().apply { lastScore = 88 }
        val vm = vm(store)
        playToEnd(vm)
        assertEquals("지난 주행보다 6점 올랐어요.", vm.report.value?.deltaLine)
        assertEquals(94, store.lastScore) // 다음 비교를 위해 이번 점수로 바뀐다
    }

    @Test fun dismissClearsTheReport() = runTest {
        val vm = vm()
        playToEnd(vm)
        assertNotNull(vm.report.value)
        vm.dismissReport()
        assertNull(vm.report.value)
    }

    @Test fun newSessionClearsTheReport() = runTest {
        val vm = vm()
        playToEnd(vm)
        assertNotNull(vm.report.value)
        vm.togglePlay() // 다시 재생: 새 세션
        runCurrent()
        assertNull(vm.report.value)
    }

    @Test fun oneLinerFollowsTheSavedPersonality() = runTest {
        val sturdy = vm(MemoryProfileStore())
        playToEnd(sturdy)
        val cute = vm(MemoryProfileStore().apply { save(PetProfile("콩이", Personality.CUTE, 0xFF4F7CFFL, true)) })
        playToEnd(cute)
        assertTrue(sturdy.report.value!!.oneLiner != cute.report.value!!.oneLiner)
    }

    @Test fun profileIsLoadedFromTheStore() = runTest {
        val p = PetProfile("장수", Personality.STURDY, 0xFF4F7CFFL, true)
        val vm = vm(MemoryProfileStore().apply { save(p) })
        runCurrent()
        assertEquals(p, vm.profile.value)
    }

    @Test fun noProfileMeansNull() = runTest {
        val vm = vm()
        runCurrent()
        assertNull(vm.profile.value)
    }

    @Test fun onProfileSavedReloadsAndGreetsOnceWhenReady() = runTest {
        val store = MemoryProfileStore()
        val greeted = mutableListOf<String>()
        val vm = DemoViewModel(
            vehicleSource = FakeVehicleSource(),
            scenario = DemoScenarios.demoCommute,
            pacing = Pacing.REAL_TIME,
            initialMode = SignalMode.DEMO,
            profileStore = store,
            greetingSink = { greeted += it; true },
        )
        runCurrent()
        store.save(PetProfile("콩", Personality.LIVELY, 0xFF4F7CFFL, true))
        vm.onProfileSaved()
        runCurrent()
        assertEquals(Personality.LIVELY, vm.profile.value?.personality)
        assertTrue("제한 값을 받기 전(정적)에는 말하지 않는다", greeted.isEmpty())
        vm.togglePlay(); runCurrent() // 시나리오 첫 신호가 제한 없음을 알려 모션 등급이 FULL 이 된다
        assertEquals(listOf(com.example.dashpet.engine.report.Greetings.first("콩", SpeechTone.LIVELY)), greeted)
        vm.onForeground(); runCurrent()
        assertEquals(1, greeted.size) // 한 번만
    }
}
