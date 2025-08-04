package com.example.diaensho.audio

import ai.picovoice.porcupine.*
import android.Manifest
import android.content.Context
import android.util.Log
import androidx.annotation.RequiresPermission
import com.example.diaensho.BuildConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WakeWordDetector @Inject constructor(
    private val context: Context,
    private val audioManager: AudioManager
) {
    companion object {
        private const val TAG = "WakeWordDetector"
        // Using built-in keywords that sound similar to "dear diary"
        private val BUILT_IN_KEYWORDS = arrayOf(
            Porcupine.BuiltInKeyword.COMPUTER,
            Porcupine.BuiltInKeyword.ALEXA
        )
        // Access key is now loaded from BuildConfig (secure)
    }

    private var porcupine: Porcupine? = null
    private var isListening = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var audioBuffer = mutableListOf<Short>()

    private val _detectionState = MutableStateFlow(DetectionState.IDLE)
    val detectionState: StateFlow<DetectionState> = _detectionState

    private val _lastDetection = MutableStateFlow<String?>(null)
    val lastDetection: StateFlow<String?> = _lastDetection

    enum class DetectionState {
        IDLE, INITIALIZING, LISTENING, ERROR
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun startListening(onWakeWordDetected: (String) -> Unit): Boolean {
        if (isListening) {
            Log.w(TAG, "Already listening for wake words")
            return false
        }

        return try {
            _detectionState.value = DetectionState.INITIALIZING

            // Initialize Porcupine with built-in keywords (free tier)
            val porcupineBuilder = Porcupine.Builder()
                .setAccessKey(BuildConfig.PORCUPINE_ACCESS_KEY) // Empty for free tier with built-in keywords
                .setKeywords(BUILT_IN_KEYWORDS)

            porcupine = porcupineBuilder.build(context)

            val frameLength = porcupine?.frameLength ?: 0
            if (frameLength <= 0) {
                Log.e(TAG, "Invalid frame length: $frameLength")
                _detectionState.value = DetectionState.ERROR
                return false
            }

            Log.i(TAG, "Porcupine initialized with frame length: $frameLength")

            // Start audio recording
            val audioStarted = audioManager.startRecording { audioData ->
                processAudioForWakeWord(audioData, frameLength, onWakeWordDetected)
            }

            if (!audioStarted) {
                Log.e(TAG, "Failed to start audio recording")
                _detectionState.value = DetectionState.ERROR
                return false
            }

            isListening = true
            _detectionState.value = DetectionState.LISTENING
            Log.i(TAG, "Wake word detection started successfully")
            true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start wake word detection", e)
            _detectionState.value = DetectionState.ERROR
            false
        }
    }

    private fun processAudioForWakeWord(
        audioData: ShortArray,
        frameLength: Int,
        onWakeWordDetected: (String) -> Unit
    ) {
        try {
            // Add new audio data to buffer
            audioBuffer.addAll(audioData.toList())

            // Process audio in chunks of the required frame length
            while (audioBuffer.size >= frameLength) {
                val frame = audioBuffer.take(frameLength).toShortArray()
                audioBuffer = audioBuffer.drop(frameLength).toMutableList()

                val keywordIndex = porcupine?.process(frame) ?: -1

                if (keywordIndex >= 0 && keywordIndex < BUILT_IN_KEYWORDS.size) {
                    val detectedKeyword = when (BUILT_IN_KEYWORDS[keywordIndex]) {
                        Porcupine.BuiltInKeyword.COMPUTER -> "computer"
                        Porcupine.BuiltInKeyword.ALEXA -> "alexa"
                        else -> "unknown"
                    }

                    Log.i(TAG, "Wake word detected: $detectedKeyword")
                    _lastDetection.value = detectedKeyword

                    // For our app, we'll treat any built-in keyword as "dear diary"
                    scope.launch(Dispatchers.Main) {
                        onWakeWordDetected("dear diary")
                    }
                    break
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing audio for wake word", e)
            _detectionState.value = DetectionState.ERROR
        }
    }

    fun stopListening() {
        if (!isListening) return

        isListening = false
        audioManager.stopRecording()
        audioBuffer.clear()

        try {
            porcupine?.delete()
            porcupine = null
            _detectionState.value = DetectionState.IDLE
            Log.i(TAG, "Wake word detection stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping wake word detection", e)
        }
    }

    fun release() {
        stopListening()
        scope.cancel()
    }
}
