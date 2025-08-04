package com.example.diaensho.audio

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.*
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

    fun startRecording(onAudioData: (ShortArray) -> Unit): Boolean {
        if (isRecording) {
            Log.w(TAG, "Already recording")
            return false
        }

        return try {
            val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
                Log.e(TAG, "Invalid buffer size: $bufferSize")
                return false
            }

            val actualBufferSize = bufferSize * BUFFER_SIZE_FACTOR

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                actualBufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord not initialized")
                audioRecord?.release()
                audioRecord = null
                return false
            }

            audioRecord?.startRecording()
            isRecording = true

            recordingJob = scope.launch {
                val buffer = ShortArray(bufferSize / 2) // Short array is half the size of byte buffer

                while (isRecording && !Thread.currentThread().isInterrupted) {
                    try {
                        val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                        if (bytesRead > 0) {
                            val audioData = buffer.copyOf(bytesRead)
                            onAudioData(audioData)
                        } else if (bytesRead < 0) {
                            Log.e(TAG, "Error reading audio data: $bytesRead")
                            break
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in recording loop", e)
                        break
                    }
                }
            }

            Log.i(TAG, "Audio recording started successfully")
            true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio recording", e)
            stopRecording()
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
            Log.i(TAG, "Audio recording stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio recording", e)
        }
    }

    fun release() {
        stopRecording()
        scope.cancel()
    }
}
