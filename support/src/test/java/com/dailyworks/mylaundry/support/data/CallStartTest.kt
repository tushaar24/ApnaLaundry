package com.dailyworks.mylaundry.support.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CallStartTest {
    private val now = 1_791_460_000_000L

    @Test
    fun xiaomiNameStampIsTheCallStart() {
        // Real file from a test phone: name = call-log start of a 165 s call.
        assertEquals(
            1_791_453_679_550L,
            callStartFor("record-1791453679550.wav", lastModifiedMs = 1_791_453_846_000L, durationMs = 163_000, nowMs = now),
        )
    }

    @Test
    fun otherwiseSaveTimeMinusLength() {
        assertEquals(
            1_791_453_846_000L - 95_000,
            callStartFor("Call recording Ravi_081026.m4a", lastModifiedMs = 1_791_453_846_000L, durationMs = 95_000, nowMs = now),
        )
    }

    @Test
    fun ignoresImplausibleStampsAndUnknownSaveTime() {
        // A future 13-digit number isn't a start time; unknown save time falls back to now.
        assertEquals(now - 30_000, callStartFor("rec-1999999999999.wav", lastModifiedMs = 0, durationMs = 30_000, nowMs = now))
        // Phone numbers (10 digits) aren't mistaken for stamps.
        assertEquals(now - 1_000, callStartFor("9876543210.amr", lastModifiedMs = 0, durationMs = 1_000, nowMs = now))
    }
}
