package dev.endlesssea.app.ui.motion

import org.junit.Assert.*
import org.junit.Test

class MotionPolicyTest {
    @Test fun disabledPreferenceWinsOverSystemAnimation() {
        val policy = MotionPolicy.resolve(false, 1f, true)
        assertFalse(policy.enabled); assertFalse(policy.loop); assertEquals(0, policy.duration(220))
    }
    @Test fun systemZeroDisablesAllNewMotion() {
        assertFalse(MotionPolicy.resolve(true, 0f, true).enabled)
    }
    @Test fun backgroundStopsLoopsWithoutChangingThePreference() {
        val policy = MotionPolicy.resolve(true, 1f, false)
        assertTrue(policy.enabled); assertFalse(policy.loop)
    }
    @Test fun foregroundAllowsLoops() { assertTrue(MotionPolicy.resolve(true, 1f, true).loop) }
    @Test fun invalidScaleIsConservativelyDisabled() {
        for (scale in listOf(-1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertFalse(MotionPolicy.resolve(true, scale, true).enabled)
        }
    }
    @Test fun positiveSystemScaleIsNotAppliedTwice() {
        assertEquals(220, MotionPolicy.resolve(true, 2f, true).duration(220))
        assertEquals(220, MotionPolicy.resolve(true, 0.5f, true).duration(220))
    }
    @Test fun finiteDurationsStayShortAndNonNegative() {
        val policy = MotionPolicy.resolve(true, 1f, true)
        assertEquals(0, policy.duration(-1)); assertEquals(400, policy.duration(5000))
    }
}
