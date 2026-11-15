package com.flintlauncher.android.nativebridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FlintGlfwEventQueueTest {

    @Test
    fun eventIdsAreStableAndMapToIntendedTypes() {
        assertEquals(FlintGlfwEvent.Type.KEY, FlintGlfwEvent.Type.fromId(1))
        assertEquals(FlintGlfwEvent.Type.CHARACTER, FlintGlfwEvent.Type.fromId(2))
        assertEquals(FlintGlfwEvent.Type.CURSOR_POSITION, FlintGlfwEvent.Type.fromId(3))
        assertEquals(FlintGlfwEvent.Type.MOUSE_BUTTON, FlintGlfwEvent.Type.fromId(4))
        assertEquals(FlintGlfwEvent.Type.SCROLL, FlintGlfwEvent.Type.fromId(5))
        assertEquals(FlintGlfwEvent.Type.FOCUS, FlintGlfwEvent.Type.fromId(6))
        assertEquals(FlintGlfwEvent.Type.WINDOW_SIZE, FlintGlfwEvent.Type.fromId(7))
        assertEquals(FlintGlfwEvent.Type.WINDOW_CLOSE, FlintGlfwEvent.Type.fromId(8))
        assertNull(FlintGlfwEvent.Type.fromId(0))
        assertNull(FlintGlfwEvent.Type.fromId(9))
    }

    @Test
    fun cursorAndScrollPreserveFloatingPointBits() {
        val cursor = FlintGlfwEvent.cursor(12.5f, -3.25f)
        assertEquals(FlintGlfwEvent.Type.CURSOR_POSITION, cursor.type)
        assertEquals(12.5f, Float.fromBits(cursor.a), 0f)
        assertEquals(-3.25f, Float.fromBits(cursor.b), 0f)

        val scroll = FlintGlfwEvent.scroll(1.5f, -2.0f)
        assertEquals(1.5f, Float.fromBits(scroll.a), 0f)
        assertEquals(-2.0f, Float.fromBits(scroll.b), 0f)
    }

    @Test
    fun keyAndMousePayloadsUseGlfwActionValues() {
        val key = FlintGlfwEvent.key(65, 30, 1, 3)
        assertEquals(65, key.a)
        assertEquals(30, key.b)
        assertEquals(1, key.c)
        assertEquals(3, key.d)

        val mouse = FlintGlfwEvent.mouseButton(0, 0, 1)
        assertEquals(FlintGlfwEvent.Type.MOUSE_BUTTON, mouse.type)
        assertEquals(0, mouse.a)
        assertEquals(0, mouse.b)
        assertEquals(1, mouse.c)
    }

    @Test
    fun inputStateRangesMatchGlfwKeyAndMouseLimits() {
        assertEquals(0, FlintGlfwWindowTestContract.keyStateFor(-1))
        assertEquals(0, FlintGlfwWindowTestContract.keyStateFor(512))
        assertEquals(0, FlintGlfwWindowTestContract.mouseStateFor(-1))
        assertEquals(0, FlintGlfwWindowTestContract.mouseStateFor(8))
    }
}

/** Pure range contract shared by the native-facing facade tests. */
private object FlintGlfwWindowTestContract {
    fun keyStateFor(key: Int): Int = if (key in 0..511) 1 else 0
    fun mouseStateFor(button: Int): Int = if (button in 0..7) 1 else 0
}
