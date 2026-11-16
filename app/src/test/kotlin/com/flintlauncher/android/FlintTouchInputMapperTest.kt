package com.flintlauncher.android

import org.junit.Assert.assertEquals
import org.junit.Test

class FlintTouchInputMapperTest {
    @Test
    fun mapsDiagonalJoystickToTwoMovementKeys() {
        assertEquals(setOf(FlintTouchInputMapper.KEY_W, FlintTouchInputMapper.KEY_D),
            FlintTouchInputMapper.keysForVector(0.8f, -0.8f))
    }

    @Test
    fun appliesDeadZoneToSmallJoystickMovement() {
        assertEquals(emptySet<Int>(), FlintTouchInputMapper.keysForVector(0.2f, -0.2f))
    }

    @Test
    fun releasesOppositeDirectionsWhenJoystickCrossesCenter() {
        assertEquals(setOf(FlintTouchInputMapper.KEY_A),
            FlintTouchInputMapper.keysForVector(-0.9f, 0.0f))
    }
}
