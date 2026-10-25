package com.dailyworks.apnalaundry.ui.screens.assistant

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * A short voice note for the assistant: AAC in .m4a, 16 kHz mono — small
 * (~4 KB a second) and what Sarvam's speech-to-text takes as is. One at a time.
 */
class VoiceRecorder(private val context: Context) {
    private var rec: MediaRecorder? = null
    private var file: File? = null

    /** False when the mic can't start (busy, no permission). */
    fun start(): Boolean {
        cancel()
        val f = File(context.cacheDir, "assistant-voice.m4a").also { it.delete() }
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        return try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioChannels(1)
            r.setAudioSamplingRate(16_000)
            r.setAudioEncodingBitRate(32_000)
            r.setOutputFile(f.absolutePath)
            r.prepare()
            r.start()
            rec = r; file = f
            true
        } catch (e: Exception) {
            r.release()
            false
        }
    }

    /** Stops and returns the note, or null when it was too short to hold any words. */
    fun stop(): ByteArray? {
        val r = rec ?: return null
        rec = null
        // stop() throws when nothing was recorded yet (tapped right after start).
        val ok = runCatching { r.stop() }.isSuccess
        r.release()
        val f = file ?: return null
        file = null
        return if (ok && f.length() > 1_500) f.readBytes().also { f.delete() } else null.also { f.delete() }
    }

    fun cancel() {
        rec?.let { r -> runCatching { r.stop() }; r.release() }
        rec = null
        file?.delete(); file = null
    }
}
