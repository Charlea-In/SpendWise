package com.example.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

private const val TAG = "VoiceExpenseRecorder"

data class RecordedAudio(
    val bytes: ByteArray,
    val mimeType: String = "audio/mp4"
)

class VoiceExpenseRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startedAtMillis: Long = 0L

    fun startRecording(): Result<Unit> {
        cancelRecording()

        val tempFile = File(context.cacheDir, "voice_expense_${System.currentTimeMillis()}.m4a")
        val mediaRecorder = createMediaRecorder()

        return try {
            mediaRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(16_000)
                setAudioEncodingBitRate(32_000)
                setAudioChannels(1)
                setOutputFile(tempFile.absolutePath)
                prepare()
                start()
            }
            recorder = mediaRecorder
            outputFile = tempFile
            startedAtMillis = System.currentTimeMillis()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start MediaRecorder", e)
            runCatching { mediaRecorder.release() }
            runCatching { tempFile.delete() }
            recorder = null
            outputFile = null
            Result.failure(
                IllegalStateException(
                    e.localizedMessage ?: "Unable to start microphone recording."
                )
            )
        }
    }

    fun stopRecording(): Result<RecordedAudio> {
        val activeRecorder = recorder
            ?: return Result.failure(IllegalStateException("No active voice recording."))
        val file = outputFile
        val elapsedMillis = System.currentTimeMillis() - startedAtMillis

        recorder = null
        outputFile = null

        return try {
            activeRecorder.stop()
            activeRecorder.release()

            if (elapsedMillis < 350L || file == null || !file.exists() || file.length() <= 0L) {
                runCatching { file?.delete() }
                return Result.failure(
                    IllegalStateException("Recording was too short. Please tap the mic and speak your expense.")
                )
            }

            val bytes = file.readBytes()
            runCatching { file.delete() }

            if (bytes.isEmpty()) {
                Result.failure(IllegalStateException("Recorded audio was empty. Please try again."))
            } else {
                Result.success(RecordedAudio(bytes = bytes, mimeType = "audio/mp4"))
            }
        } catch (e: RuntimeException) {
            Log.e(TAG, "Failed to stop MediaRecorder cleanly", e)
            runCatching { activeRecorder.release() }
            runCatching { file?.delete() }
            Result.failure(
                IllegalStateException("Recording stopped too quickly. Speak for at least a second and try again.")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error reading recorded audio", e)
            runCatching { activeRecorder.release() }
            runCatching { file?.delete() }
            Result.failure(
                IllegalStateException(e.localizedMessage ?: "Failed to read recorded audio.")
            )
        }
    }

    fun cancelRecording() {
        val activeRecorder = recorder
        val file = outputFile
        recorder = null
        outputFile = null

        if (activeRecorder != null) {
            runCatching { activeRecorder.stop() }
            runCatching { activeRecorder.release() }
        }
        if (file != null) {
            runCatching { file.delete() }
        }
    }

    fun readNormalizedAmplitude(): Float {
        val active = recorder ?: return 0f
        return try {
            val amp = active.maxAmplitude
            (amp.toFloat() / 18_000f).coerceIn(0f, 1f)
        } catch (_: Exception) {
            0f
        }
    }

    @Suppress("DEPRECATION")
    private fun createMediaRecorder(): MediaRecorder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }
    }
}
