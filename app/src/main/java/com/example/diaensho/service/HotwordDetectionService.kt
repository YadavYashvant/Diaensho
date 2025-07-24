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
        const val END_PHRASE = "that's it"
        private const val RESTART_DELAY_MS = 1000L
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 2000L
        private const val RECORDING_TIMEOUT_MS = 300000L // 5 minutes max
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
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
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
        val recordIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        updateNotification("Recording diary entry... Say \"that's it\" when done")

        // Set up recording timeout
        recordingTimeoutJob = serviceScope.launch {
            delay(RECORDING_TIMEOUT_MS)
            updateNotification("Recording timed out after 5 minutes")
            speechRecognizer?.stopListening()
        }

        var currentEntry = StringBuilder()
        var isFirstResult = true

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                updateNotification("Listening... Say \"that's it\" when done")
            }

            override fun onBeginningOfSpeech() {
                updateNotification("Recording your diary entry...")
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val partialText = matches?.firstOrNull()
                if (!partialText.isNullOrBlank()) {
                    if (partialText.lowercase().trim() == END_PHRASE) {
                        updateNotification("Saving diary entry...")
                        saveDiaryEntry(currentEntry.toString())
                    } else {
                        if (isFirstResult) {
                            // Skip the "dear diary" phrase from the actual entry
                            if (!partialText.lowercase().contains(HOTWORD)) {
                                currentEntry.append(partialText)
                            }
                            isFirstResult = false
                        } else {
                            if (currentEntry.isNotEmpty()) currentEntry.append(" ")
                            currentEntry.append(partialText)
                        }
                        updateNotification("Recording: $partialText")
                    }
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()

                if (!text.isNullOrBlank()) {
                    if (text.lowercase().trim() == END_PHRASE) {
                        updateNotification("Saving diary entry...")
                        saveDiaryEntry(currentEntry.toString())
                    } else {
                        if (isFirstResult) {
                            if (!text.lowercase().contains(HOTWORD)) {
                                currentEntry.append(text)
                            }
                            isFirstResult = false
                        } else {
                            if (currentEntry.isNotEmpty()) currentEntry.append(" ")
                            currentEntry.append(text)
                        }
                        // Continue listening
                        try {
                            speechRecognizer?.startListening(recordIntent)
                        } catch (e: Exception) {
                            Log.e("HotwordService", "Error restarting listening", e)
                            handler.postDelayed({
                                try {
                                    speechRecognizer?.startListening(recordIntent)
                                } catch (e: Exception) {
                                    saveDiaryEntry(currentEntry.toString())
                                }
                            }, 1000)
                        }
                    }
                }
            }

            override fun onError(error: Int) {
                val errorMessage = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> {
                        // If we have content and get a no-match error, just keep listening
                        if (currentEntry.isNotEmpty()) {
                            try {
                                speechRecognizer?.startListening(recordIntent)
                                return
                            } catch (e: Exception) {
                                "Speech recognition error, saving current entry"
                            }
                        } else {
                            "No speech detected, please try again"
                        }
                    }
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                        if (currentEntry.isNotEmpty()) {
                            "Speech timeout, saving current entry"
                        } else {
                            "Speech timeout, please try again"
                        }
                    }
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                        handler.postDelayed({
                            try {
                                speechRecognizer?.startListening(recordIntent)
                            } catch (e: Exception) {
                                saveDiaryEntry(currentEntry.toString())
                            }
                        }, 1000)
                        return
                    }
                    else -> "Recognition error: $error"
                }

                Log.e("HotwordService", "Error recording diary entry: $error ($errorMessage)")
                updateNotification(errorMessage)

                if (currentEntry.isNotEmpty()) {
                    saveDiaryEntry(currentEntry.toString())
                } else {
                    resetToHotwordDetection()
                }
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Update notification with audio level indicator
                val level = (rmsdB / 10).toInt().coerceIn(0, 5)
                val indicator = "▌".repeat(level)
                updateNotification("Recording: $indicator")
            }

            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        try {
            speechRecognizer?.startListening(recordIntent)
        } catch (e: Exception) {
            Log.e("HotwordService", "Error starting diary entry recording", e)
            updateNotification("Failed to start recording, trying again...")
            handler.postDelayed({
                try {
                    speechRecognizer?.startListening(recordIntent)
                } catch (e: Exception) {
                    resetToHotwordDetection()
                }
            }, 1000)
        }
    }

    private fun saveDiaryEntry(text: String) {
        if (text.isBlank()) {
            updateNotification("No entry recorded")
            resetToHotwordDetection()
            return
        }

        recordingTimeoutJob?.cancel()
        serviceScope.launch {
            try {
                repository.addDiaryEntry(text)
                updateNotification("Entry saved successfully!")
            } catch (e: Exception) {
                Log.e("HotwordService", "Failed to save entry", e)
                updateNotification("Failed to save entry")
            } finally {
                delay(2000)
                resetToHotwordDetection()
            }
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
