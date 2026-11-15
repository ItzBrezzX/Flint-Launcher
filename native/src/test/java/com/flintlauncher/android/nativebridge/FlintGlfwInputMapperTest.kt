package com.flintlauncher.android.nativebridge

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FlintGlfwInputMapperTest {
    @Test
    fun mapsLettersDigitsAndFunctionKeysToGlfwValues() {
        assertEquals(FlintGlfwInputMapper.GLFW_KEY_A, FlintGlfwInputMapper.key(KeyEvent.KEYCODE_A))
        assertEquals(FlintGlfwInputMapper.GLFW_KEY_A + 25, FlintGlfwInputMapper.key(KeyEvent.KEYCODE_Z))
        assertEquals(FlintGlfwInputMapper.GLFW_KEY_0, FlintGlfwInputMapper.key(KeyEvent.KEYCODE_0))
        assertEquals(FlintGlfwInputMapper.GLFW_KEY_0 + 9, FlintGlfwInputMapper.key(KeyEvent.KEYCODE_9))
        assertEquals(FlintGlfwInputMapper.GLFW_KEY_F1 + 11, FlintGlfwInputMapper.key(KeyEvent.KEYCODE_F12))
    }

    @Test
    fun mapsNavigationAndModifiers() {
        assertEquals(FlintGlfwInputMapper.GLFW_KEY_ESCAPE, FlintGlfwInputMapper.key(KeyEvent.KEYCODE_ESCAPE))
        assertEquals(FlintGlfwInputMapper.GLFW_KEY_LEFT, FlintGlfwInputMapper.key(KeyEvent.KEYCODE_DPAD_LEFT))
        assertEquals(FlintGlfwInputMapper.GLFW_KEY_LEFT_CONTROL, FlintGlfwInputMapper.key(KeyEvent.KEYCODE_CTRL_LEFT))
    }

    @Test
    fun convertsAndroidMetaStateToGlfwModifierMask() {
        val androidMeta = KeyEvent.META_SHIFT_ON or KeyEvent.META_CTRL_ON or KeyEvent.META_CAPS_LOCK_ON
        val expected = FlintGlfwInputMapper.GLFW_MOD_SHIFT or
            FlintGlfwInputMapper.GLFW_MOD_CONTROL or FlintGlfwInputMapper.GLFW_MOD_CAPS_LOCK
        assertEquals(expected, FlintGlfwInputMapper.modifiers(androidMeta))
    }

    @Test
    fun dropsAndroidKeysWithoutAStableGlfwEquivalent() {
        assertNull(FlintGlfwInputMapper.key(KeyEvent.KEYCODE_CAMERA))
    }
}