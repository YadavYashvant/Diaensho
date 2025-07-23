package com.example.diaensho.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.example.diaensho.data.repository.MainRepository
import com.example.diaensho.notification.NotificationHelper
import com.example.diaensho.util.PowerManagerHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.os.Bundle
import android.os.Handler
import android.os.Looper

@AndroidEntryPoint
class HotwordDetectionService : Service() {
    companion object {
        const val HOTWORD = "dear diary"
        private const val RESTART_DELAY_MS = 1000L
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 2000L
        private const val RECORDING_TIMEOUT_MS = 30000L // 30 seconds
    }

    @Inject lateinit var repository: MainRepository
    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var powerManagerHelper: PowerManagerHelper

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private val handler = Handler(Looper.getMainLooper())
    private var restartRunnable: Runnable? = null
    private var retryCount = 0
    private var recordingTimeoutJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(
            NotificationHelper.HOTWORD_NOTIFICATION_ID,
            notificationHelper.createHotwordNotification()
        )
        powerManagerHelper.acquireWakeLock()
        initializeSpeechRecognizer()
    }

    private fun initializeSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.e("HotwordService", "Speech recognition not available")
            stopSelf()
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(createRecognitionListener())
        }

        startListening()
    }

    private fun startListening() {
        if (isListening) return

        try {
            val intent = RecognizerIntent.getVoiceDetailsIntent(this).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            }
            isListening = true
            retryCount = 0
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e("HotwordService", "Error starting speech recognition", e)
            handleError()
        }
    }

    private fun handleError() {
        if (retryCount < MAX_RETRY_ATTEMPTS) {
            retryCount++
            handler.postDelayed({ startListening() }, RETRY_DELAY_MS)
        } else {
            retryCount = 0
            scheduleRestart()
        }
    }

    private fun createRecognitionListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            updateNotification("Listening for hotword...")
            retryCount = 0
        }

        override fun onBeginningOfSpeech() {
            updateNotification("Speech detected...")
        }

        override fun onRmsChanged(rmsdB: Float) {}

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            isListening = false
        }

        override fun onError(error: Int) {
            isListening = false
            val errorMessage = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                SpeechRecognizer.ERROR_NETWORK -> "Network error"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                else -> "Recognition error: $error"
            }
            Log.d("HotwordService", errorMessage)
            handleError()
        }

        override fun onResults(results: Bundle?) {
            isListening = false
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull()?.lowercase()

            if (text?.contains(HOTWORD) == true) {
                updateNotification("Hotword detected! Recording diary entry...")
                startRecordingDiaryEntry()
            } else {
                scheduleRestart()
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull()?.lowercase()

            if (text?.contains(HOTWORD) == true) {
                updateNotification("Hotword detected! Recording diary entry...")
                speechRecognizer?.stopListening()
                startRecordingDiaryEntry()
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun startRecordingDiaryEntry() {
        val recordIntent = RecognizerIntent.getVoiceDetailsIntent(this).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Recording your diary entry...")
        }

        // Set up recording timeout
        recordingTimeoutJob = serviceScope.launch {
            delay(RECORDING_TIMEOUT_MS)
            speechRecognizer?.stopListening()
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                updateNotification("Recording diary entry...")
            }

            override fun onResults(results: Bundle?) {
                recordingTimeoutJob?.cancel()
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val entryText = matches?.firstOrNull()

                if (!entryText.isNullOrBlank()) {
                    serviceScope.launch {
                        try {
                            repository.addDiaryEntry(entryText)
                            updateNotification("Entry saved successfully!")
                        } catch (e: Exception) {
                            Log.e("HotwordService", "Failed to save entry", e)
                            updateNotification("Failed to save entry")
                        } finally {
                            delay(2000) // Show success/error message briefly
                            resetToHotwordDetection()
                        }
                    }
                } else {
                    updateNotification("No speech detected")
                    resetToHotwordDetection()
                }
            }

            override fun onError(error: Int) {
                recordingTimeoutJob?.cancel()
                Log.e("HotwordService", "Error recording diary entry: $error")
                updateNotification("Error recording entry")
                resetToHotwordDetection()
            }

            // Implement remaining RecognitionListener methods
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        try {
            speechRecognizer?.startListening(recordIntent)
        } catch (e: Exception) {
            Log.e("HotwordService", "Error starting diary entry recording", e)
            recordingTimeoutJob?.cancel()
            resetToHotwordDetection()
        }
    }

    private fun resetToHotwordDetection() {
        handler.postDelayed({
            speechRecognizer?.setRecognitionListener(createRecognitionListener())
            startListening()
        }, 2000)
    }

    private fun updateNotification(message: String) {
        notificationHelper.updateHotwordNotification(message)
    }

    private fun scheduleRestart() {
        restartRunnable?.let { handler.removeCallbacks(it) }

        restartRunnable = Runnable {
            startListening()
        }.also {
            handler.postDelayed(it, RESTART_DELAY_MS)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startListening()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        recordingTimeoutJob?.cancel()
        speechRecognizer?.destroy()
        restartRunnable?.let { handler.removeCallbacks(it) }
        powerManagerHelper.releaseWakeLock()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
