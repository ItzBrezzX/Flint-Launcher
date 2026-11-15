package com.flintlauncher.android.nativebridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FlintInputEventTest {
    @Test
    fun decodesPackedNativeEventWithoutLosingCoordinates() {
        val packed = longArrayOf(
            FlintInputEvent.CURSOR.toLong(),
            7L,
            0L,
            3L,
            java.lang.Double.doubleToRawLongBits(123.5),
            java.lang.Double.doubleToRawLongBits(456.25),
        )

        val event = FlintInputEvent.decode(packed).single()

        assertEquals(FlintInputEvent.CURSOR, event.type)
        assertEquals(7, event.code)
        assertEquals(3, event.modifiers)
        assertEquals(123.5, event.x, 0.0)
        assertEquals(456.25, event.y, 0.0)
    }

    @Test
    fun rejectsTruncatedNativeEventRecords() {
        assertThrows(IllegalArgumentException::class.java) {
            FlintInputEvent.decode(longArrayOf(1L, 2L, 3L))
        }
    }

    @Test
    fun decodesMultipleEventsInQueueOrder() {
        val packed = longArrayOf(
            FlintInputEvent.KEY.toLong(), 29L, 1L, 0L, 0L, 0L,
            FlintInputEvent.FOCUS.toLong(), 0L, 1L, 0L, 0L, 0L,
        )

        val events = FlintInputEvent.decode(packed)

        assertEquals(2, events.size)
        assertEquals(FlintInputEvent.KEY, events[0].type)
        assertEquals(FlintInputEvent.FOCUS, events[1].type)
    }
}
