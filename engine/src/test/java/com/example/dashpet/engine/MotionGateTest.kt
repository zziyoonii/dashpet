package com.example.dashpet.engine

import com.example.dashpet.signal.RestrictionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MotionGateTest {
    private val gate = MotionGate(EngineConfig())

    @Test
    fun noRestrictionAllowsFullMotion() {
        assertEquals(MotionTier.FULL, gate.tier(RestrictionState.NONE))
    }

    @Test
    fun activeRestrictionIsStatic() {
        assertEquals(MotionTier.STATIC, gate.tier(RestrictionState.ACTIVE))
    }

    @Test
    fun unknownRestrictionIsStatic() {
        assertEquals(MotionTier.STATIC, gate.tier(RestrictionState.UNKNOWN))
    }

    @Test
    fun oneShotIsNeverOutputByDefault() {
        for (state in RestrictionState.entries) {
            for (candidate in listOf(false, true)) {
                assertNotEquals(MotionTier.ONE_SHOT, gate.tier(state, candidate))
            }
        }
    }

    @Test
    fun oneShotIsPossibleOnlyWhenEnabledAndRestrictionActive() {
        val enabled = MotionGate(EngineConfig(allowOneShot = true))
        assertEquals(MotionTier.ONE_SHOT, enabled.tier(RestrictionState.ACTIVE, oneShotCandidate = true))
        assertEquals(MotionTier.STATIC, enabled.tier(RestrictionState.ACTIVE, oneShotCandidate = false))
        // UNKNOWN 은 항상 STATIC
        assertEquals(MotionTier.STATIC, enabled.tier(RestrictionState.UNKNOWN, oneShotCandidate = true))
        assertEquals(MotionTier.FULL, enabled.tier(RestrictionState.NONE, oneShotCandidate = true))
    }
}
