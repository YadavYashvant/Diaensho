package com.example.diaensho.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton
import java.util.*

@Singleton
class SpeechToTextManager @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "SpeechToTextManager"
        private const val RECOGNITION_TIMEOUT_MS = 30000L // 30 seconds
        private const val SILENCE_TIMEOUT_MS = 5000L // 5 seconds of silence
        private const val MAX_RETRY_ATTEMPTS = 3
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var currentRetryCount = 0
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var recognitionTimeoutJob: Job? = null
    private var silenceTimeoutJob: Job? = null

    private val _recognitionState = MutableStateFlow(RecognitionState.IDLE)
    val recognitionState: StateFlow<RecognitionState> = _recognitionState

    private val _partialResult = MutableStateFlow("")
    val partialResult: StateFlow<String> = _partialResult

    private val _finalResult = MutableStateFlow("")
    val finalResult: StateFlow<String> = _finalResult

    private val _confidence = MutableStateFlow(0f)
    val confidence: StateFlow<Float> = _confidence

    enum class RecognitionState {
        IDLE, INITIALIZING, LISTENING, PROCESSING, ERROR, COMPLETED
    }

    data class RecognitionResult(
        val text: String,
        val confidence: Float,
        val isPartial: Boolean = false
    )

    fun startListening(
        onResult: (RecognitionResult) -> Unit,
        onError: (String) -> Unit,
        onComplete: () -> Unit,
        preferOffline: Boolean = false,
        maxResults: Int = 5
    ): Boolean {
        if (isListening) {
            Log.w(TAG, "Already listening")
            return false
        }

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.e(TAG, "Speech recognition not available")
            onError("Speech recognition not available")
            return false
        }

        return try {
            _recognitionState.value = RecognitionState.INITIALIZING
            _partialResult.value = ""
            _finalResult.value = ""
            _confidence.value = 0f

            // Create and configure speech recognizer
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            
            if (speechRecognizer == null) {
                Log.e(TAG, "Failed to create SpeechRecognizer")
                onError("Failed to initialize speech recognition")
                return false
            }

            val recognitionIntent = createRecognitionIntent(preferOffline, maxResults)
            
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d(TAG, "Ready for speech")
                    _recognitionState.value = RecognitionState.LISTENING
                    startRecognitionTimeout(onError, onComplete)
                    currentRetryCount = 0
                }

                override fun onBeginningOfSpeech() {
                    Log.d(TAG, "Beginning of speech detected")
                    cancelSilenceTimeout()
                    _recognitionState.value = RecognitionState.LISTENING
                }

                override fun onRmsChanged(rmsdB: Float) {
                    // Normalize RMS to 0-1 range for UI feedback
                    val normalizedLevel = (rmsdB + 40f) / 60f // Typical range is -40 to 20 dB
                    _confidence.value = normalizedLevel.coerceIn(0f, 1f)
                }

                override fun onBufferReceived(buffer: ByteArray?) {
                    // Audio buffer received - can be used for additional processing
                }

                override fun onEndOfSpeech() {
                    Log.d(TAG, "End of speech")
                    _recognitionState.value = RecognitionState.PROCESSING
                    startSilenceTimeout(onError, onComplete)
                }

                override fun onError(error: Int) {
                    isListening = false
                    cancelTimeouts()
                    
                    val errorMessage = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                        SpeechRecognizer.ERROR_CLIENT -> "Client error"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                        SpeechRecognizer.ERROR_NETWORK -> "Network error"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                        SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                        SpeechRecognizer.ERROR_SERVER -> "Server error"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                        else -> "Unknown error: $error"
                    }
                    
                    Log.e(TAG, "Speech recognition error: $errorMessage")
                    
                    // Retry logic for certain errors
                    if (shouldRetry(error) && currentRetryCount < MAX_RETRY_ATTEMPTS) {
                        currentRetryCount++
                        Log.i(TAG, "Retrying recognition (attempt $currentRetryCount)")
                        
                        scope.launch {
                            delay(1000) // Wait before retry
                            if (!startListening(onResult, onError, onComplete, preferOffline, maxResults)) {
                                _recognitionState.value = RecognitionState.ERROR
                                onError(errorMessage)
                            }
                        }
                    } else {
                        _recognitionState.value = RecognitionState.ERROR
                        onError(errorMessage)
                    }
                }

                override fun onResults(results: Bundle?) {
                    isListening = false
                    cancelTimeouts()
                    
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val confidenceScores = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
                    
                    if (matches != null && matches.isNotEmpty()) {
                        val bestMatch = matches[0]
                        val confidence = confidenceScores?.getOrNull(0) ?: 0f
                        
                        Log.d(TAG, "Final result: $bestMatch (confidence: $confidence)")
                        
                        _finalResult.value = bestMatch
                        _confidence.value = confidence
                        _recognitionState.value = RecognitionState.COMPLETED
                        
                        onResult(RecognitionResult(bestMatch, confidence, isPartial = false))
                        onComplete()
                    } else {
                        Log.w(TAG, "No results received")
                        _recognitionState.value = RecognitionState.ERROR
                        onError("No speech detected")
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val partial = matches?.firstOrNull() ?: ""
                    
                    if (partial.isNotEmpty()) {
                        Log.d(TAG, "Partial result: $partial")
                        _partialResult.value = partial
                        onResult(RecognitionResult(partial, 0f, isPartial = true))
                        
                        // Reset silence timeout as we're getting partial results
                        resetSilenceTimeout(onError, onComplete)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {
                    Log.d(TAG, "Recognition event: $eventType")
                }
            })

            isListening = true
            speechRecognizer?.startListening(recognitionIntent)
            Log.i(TAG, "Speech recognition started")
            true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start speech recognition", e)
            _recognitionState.value = RecognitionState.ERROR
            onError("Failed to start speech recognition: ${e.message}")
            false
        }
    }

    private fun createRecognitionIntent(preferOffline: Boolean, maxResults: Int): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, maxResults)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            
            // Enhanced audio settings for better recognition
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, SILENCE_TIMEOUT_MS)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, SILENCE_TIMEOUT_MS / 2)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1000)
            
            // Request confidence scores
            putExtra(RecognizerIntent.EXTRA_CONFIDENCE_SCORES, true)
        }
    }

    private fun shouldRetry(error: Int): Boolean {
        return when (error) {
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
            SpeechRecognizer.ERROR_SERVER,
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> true
            else -> false
        }
    }

    private fun startRecognitionTimeout(onError: (String) -> Unit, onComplete: () -> Unit) {
        recognitionTimeoutJob = scope.launch {
            delay(RECOGNITION_TIMEOUT_MS)
            if (isListening) {
                Log.w(TAG, "Recognition timeout")
                stopListening()
                _recognitionState.value = RecognitionState.ERROR
                onError("Recognition timed out")
            }
        }
    }

    private fun startSilenceTimeout(onError: (String) -> Unit, onComplete: () -> Unit) {
        silenceTimeoutJob = scope.launch {
            delay(SILENCE_TIMEOUT_MS)
            if (isListening) {
                Log.i(TAG, "Silence timeout - completing recognition")
                stopListening()
                if (_partialResult.value.isNotEmpty()) {
                    _finalResult.value = _partialResult.value
                    _recognitionState.value = RecognitionState.COMPLETED
                    onComplete()
                } else {
                    _recognitionState.value = RecognitionState.ERROR
                    onError("No speech detected after silence")
                }
            }
        }
    }

    private fun resetSilenceTimeout(onError: (String) -> Unit, onComplete: () -> Unit) {
        cancelSilenceTimeout()
        startSilenceTimeout(onError, onComplete)
    }

    private fun cancelTimeouts() {
        recognitionTimeoutJob?.cancel()
        silenceTimeoutJob?.cancel()
    }

    private fun cancelSilenceTimeout() {
        silenceTimeoutJob?.cancel()
    }

    fun stopListening() {
        if (!isListening) return

        isListening = false
        cancelTimeouts()
        
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
            speechRecognizer = null
            
            if (_recognitionState.value != RecognitionState.COMPLETED) {
                _recognitionState.value = RecognitionState.IDLE
            }
            
            Log.i(TAG, "Speech recognition stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping speech recognition", e)
        }
    }

    fun release() {
        stopListening()
        scope.cancel()
    }
}
