package com.pinealctx.nexus.ui.screens.chat

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import java.io.File

data class VoiceRecording(
    val file: File,
    val durationMs: Int
)

class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startedAtMs: Long = 0L

    val isRecording: Boolean
        get() = recorder != null

    fun start() {
        check(recorder == null) { "A voice recording is already in progress" }
        val target = File.createTempFile("nexus_voice_", ".m4a", context.cacheDir)
        val nextRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        try {
            nextRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(96_000)
                setAudioSamplingRate(44_100)
                setOutputFile(target.absolutePath)
                prepare()
                start()
            }
            outputFile = target
            recorder = nextRecorder
            startedAtMs = SystemClock.elapsedRealtime()
        } catch (error: Exception) {
            runCatching { nextRecorder.release() }
            target.delete()
            throw error
        }
    }

    fun stop(): VoiceRecording {
        val activeRecorder = recorder ?: error("No voice recording is in progress")
        val target = outputFile ?: error("Recording output is unavailable")
        val elapsed = (SystemClock.elapsedRealtime() - startedAtMs)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
        try {
            activeRecorder.stop()
        } catch (error: RuntimeException) {
            target.delete()
            throw IllegalStateException("The recording was too short or could not be saved", error)
        } finally {
            activeRecorder.reset()
            activeRecorder.release()
            recorder = null
            outputFile = null
            startedAtMs = 0L
        }
        return VoiceRecording(target, elapsed)
    }

    fun cancel() {
        val target = outputFile
        recorder?.let { activeRecorder ->
            runCatching { activeRecorder.stop() }
            activeRecorder.reset()
            activeRecorder.release()
        }
        recorder = null
        outputFile = null
        startedAtMs = 0L
        target?.delete()
    }
}
