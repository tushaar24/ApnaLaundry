package com.dailyworks.mylaundry.support.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecordingPickTest {
    private val start = 1_000_000_000_000L
    private val end = start + 95_000

    private fun file(name: String, modified: Long, size: Long = 50_000, folder: String = "") =
        RecordingCandidate("content://test/$name", name, modified, size, null, folder)

    @Test
    fun prefersFileNamedWithTheNumber() {
        val picked = pickRecording(
            listOf(
                file("Recording_0031.m4a", end + 2_000),
                file("Call recording 98765 43210_081026.m4a", end + 9_000),
            ),
            phone = "9876543210", startedAtMs = start, endedAtMs = end,
        )
        assertEquals("Call recording 98765 43210_081026.m4a", picked?.name)
    }

    @Test
    fun otherwiseClosestToCallEndInACallFolder() {
        val picked = pickRecording(
            listOf(
                file("voice_note.m4a", end + 1_000, folder = "Music/WhatsApp"),
                file("rec_a.amr", end + 4_000, folder = "Recordings/Call"),
                file("rec_b.amr", end + 300_000, folder = "Recordings/Call"),
            ),
            phone = "9876543210", startedAtMs = start, endedAtMs = end,
        )
        assertEquals("rec_a.amr", picked?.name)
    }

    @Test
    fun ignoresFilesOutsideTheWindowEmptyOrNotAudio() {
        val picked = pickRecording(
            listOf(
                file("old 9876543210.m4a", start - 60_000), // before the call
                file("late 9876543210.m4a", end + 11 * 60_000), // >10 min after
                file("empty 9876543210.m4a", end, size = 0),
                file("photo 9876543210.jpg", end),
            ),
            phone = "9876543210", startedAtMs = start, endedAtMs = end,
        )
        assertNull(picked)
    }

    @Test
    fun mimeAndExt() {
        assertEquals("amr", extOf("x.AMR"))
        assertEquals("audio/mp4", mimeFor("m4a"))
        assertEquals("audio/amr", mimeFor("amr"))
    }
}
