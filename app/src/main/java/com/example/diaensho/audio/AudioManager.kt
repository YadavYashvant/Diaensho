package com.example.diaensho.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioManager @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "AudioManager"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE_FACTOR = 2
    }

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _audioState = MutableStateFlow(AudioState.IDLE)
    val audioState: StateFlow<AudioState> = _audioState

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel

    enum class AudioState {
        IDLE, INITIALIZING, RECORDING, ERROR
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun startRecording(onAudioData: (ShortArray) -> Unit): Boolean {
        if (isRecording) {
            Log.w(TAG, "Already recording")
            return false
        }

        if (!hasRecordPermission()) {
            Log.e(TAG, "No record permission")
            _audioState.value = AudioState.ERROR
            return false
        }

        return try {
            _audioState.value = AudioState.INITIALIZING

            val bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            ) * BUFFER_SIZE_FACTOR

            if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
                Log.e(TAG, "Invalid buffer size: $bufferSize")
                _audioState.value = AudioState.ERROR
                return false
            }

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord not initialized properly")
                _audioState.value = AudioState.ERROR
                return false
            }

            audioRecord?.startRecording()
            isRecording = true
            _audioState.value = AudioState.RECORDING

            recordingJob = scope.launch {
                val buffer = ShortArray(bufferSize / 2) // Short is 2 bytes

                while (isRecording && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    val readResult = audioRecord?.read(buffer, 0, buffer.size) ?: 0

                    if (readResult > 0) {
                        // Calculate audio level for visual feedback
                        val level = calculateAudioLevel(buffer, readResult)
                        _audioLevel.value = level

                        // Pass audio data to callback
                        onAudioData(buffer.copyOf(readResult))
                    } else {
                        Log.w(TAG, "AudioRecord read error: $readResult")
                        delay(10) // Small delay to prevent tight loop
                    }
                }
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            _audioState.value = AudioState.ERROR
            false
        }
    }

    fun stopRecording() {
        if (!isRecording) return

        isRecording = false
        recordingJob?.cancel()

        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            _audioState.value = AudioState.IDLE
            _audioLevel.value = 0f
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
        }
    }

    private fun calculateAudioLevel(buffer: ShortArray, length: Int): Float {
        var sum = 0.0
        for (i in 0 until length) {
            sum += (buffer[i] * buffer[i]).toDouble()
        }
        val rms = kotlin.math.sqrt(sum / length)
        return (rms / Short.MAX_VALUE).toFloat()
    }

    private fun hasRecordPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun release() {
        stopRecording()
        scope.cancel()
    }
}
