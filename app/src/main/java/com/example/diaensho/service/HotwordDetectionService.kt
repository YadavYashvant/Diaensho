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
            // Create the intent with a check to ensure it's not null
            val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            // Verify intent was created successfully
            if (recognizerIntent == null) {
                Log.e("HotwordService", "Failed to create RecognizerIntent")
                handleError()
                return
            }

            // Apply settings
            recognizerIntent.apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
                // Add increased silence tolerance
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500)
            }

            // Check if speech recognizer is still initialized
            if (speechRecognizer == null) {
                Log.e("HotwordService", "SpeechRecognizer is null, reinitializing")
                initializeSpeechRecognizer()
                // If still null after reinitialization, handle error
                if (speechRecognizer == null) {
                    handleError()
                    return
                }
            }

            isListening = true
            retryCount = 0
            speechRecognizer?.startListening(recognizerIntent)
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
        // Create a more reliable recording intent
        val recordIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5) // Get more results for better matching
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false) // Try online for better accuracy
            // Longer silence tolerance for pauses in speech
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000)
        }

        updateNotification("Recording diary entry... Say \"that's it\" when done")

        // Initialize entry content variables before the timeout job
        val currentEntryContent = StringBuilder()
        var isRecordingEntry = false
        var hasProcessedHotword = false

        // Set up recording timeout
        recordingTimeoutJob?.cancel() // Cancel any existing job first
        recordingTimeoutJob = serviceScope.launch {
            delay(RECORDING_TIMEOUT_MS)
            updateNotification("Recording timed out after 5 minutes")
            speechRecognizer?.stopListening()
            // Save whatever content we have after timeout
            if (currentEntryContent.isNotEmpty()) {
                saveDiaryEntry(currentEntryContent.toString())
            } else {
                resetToHotwordDetection()
            }
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                updateNotification("Listening for diary entry... Say \"that's it\" when done")
                isRecordingEntry = true
            }

            override fun onBeginningOfSpeech() {
                updateNotification("Recording your diary entry...")
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val partialText = matches?.firstOrNull()?.trim() ?: return

                // Check for end phrase with more flexibility
                if (partialText.lowercase().endsWith(END_PHRASE) ||
                    partialText.lowercase() == END_PHRASE) {
                    updateNotification("End phrase detected. Saving entry...")
                    // Remove the end phrase from the entry if it's there
                    val contentWithoutEndPhrase = currentEntryContent.toString()
                        .replace(END_PHRASE, "", ignoreCase = true)
                        .trim()
                    saveDiaryEntry(contentWithoutEndPhrase)
                    return
                }

                // Process content for the diary entry
                if (!hasProcessedHotword) {
                    // If this is the first segment, filter out the hotword
                    if (partialText.lowercase().contains(HOTWORD)) {
                        // Extract text after hotword
                        val textAfterHotword = partialText.substring(
                            partialText.lowercase().indexOf(HOTWORD) + HOTWORD.length
                        ).trim()

                        if (textAfterHotword.isNotEmpty()) {
                            currentEntryContent.append(textAfterHotword)
                            updateNotification("Recording: $textAfterHotword")
                        }
                        hasProcessedHotword = true
                    } else {
                        // If no hotword, just use the text (might be after hotword was already processed)
                        currentEntryContent.append(partialText)
                        updateNotification("Recording: $partialText")
                        hasProcessedHotword = true
                    }
                } else {
                    // For subsequent parts, just append the text
                    // Only update if there's new content
                    if (partialText.isNotEmpty() &&
                        !currentEntryContent.toString().endsWith(partialText, ignoreCase = true)) {
                        if (currentEntryContent.isNotEmpty()) currentEntryContent.append(" ")
                        currentEntryContent.append(partialText)
                        updateNotification("Recording: $partialText")
                    }
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.trim() ?: return

                // Process final results
                if (text.lowercase().endsWith(END_PHRASE) || text.lowercase() == END_PHRASE) {
                    // Remove the end phrase from the entry
                    val contentWithoutEndPhrase = currentEntryContent.toString()
                        .replace(END_PHRASE, "", ignoreCase = true)
                        .trim()
                    updateNotification("End phrase detected. Saving entry...")
                    saveDiaryEntry(contentWithoutEndPhrase)
                    return
                }

                // If this isn't the end, process the result and continue listening
                if (!hasProcessedHotword) {
                    // First result, check for hotword
                    if (text.lowercase().contains(HOTWORD)) {
                        val textAfterHotword = text.substring(
                            text.lowercase().indexOf(HOTWORD) + HOTWORD.length
                        ).trim()

                        if (textAfterHotword.isNotEmpty()) {
                            currentEntryContent.append(textAfterHotword)
                        }
                        hasProcessedHotword = true
                    } else {
                        currentEntryContent.append(text)
                        hasProcessedHotword = true
                    }
                } else {
                    // For subsequent results
                    if (currentEntryContent.isNotEmpty()) currentEntryContent.append(" ")
                    currentEntryContent.append(text)
                }

                // Continue listening for more content
                updateNotification("Continuing to record... Say \"that's it\" when done")
                try {
                    speechRecognizer?.startListening(recordIntent)
                } catch (e: Exception) {
                    Log.e("HotwordService", "Error continuing recognition", e)
                    handler.postDelayed({
                        try {
                            speechRecognizer?.startListening(recordIntent)
                        } catch (e: Exception) {
                            if (currentEntryContent.isNotEmpty()) {
                                saveDiaryEntry(currentEntryContent.toString())
                            } else {
                                resetToHotwordDetection()
                            }
                        }
                    }, 1000)
                }
            }

            override fun onError(error: Int) {
                val errorMessage = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> {
                        // Keep listening if we've already started recording
                        if (currentEntryContent.isNotEmpty() || hasProcessedHotword) {
                            try {
                                Log.d("HotwordService", "No match, but continuing recording")
                                speechRecognizer?.startListening(recordIntent)
                                return
                            } catch (e: Exception) {
                                "Recognition error, saving current entry"
                            }
                        } else {
                            "No speech detected, please try again"
                        }
                    }
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                        if (currentEntryContent.isNotEmpty()) {
                            "Speech timeout, saving current entry"
                        } else {
                            "Speech timeout, no entry recorded"
                        }
                    }
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                        // If busy, release and recreate the recognizer
                        try {
                            speechRecognizer?.cancel()
                            handler.postDelayed({
                                try {
                                    speechRecognizer?.startListening(recordIntent)
                                    return@postDelayed
                                } catch (e: Exception) {
                                    // If still failing, save what we have
                                    if (currentEntryContent.isNotEmpty()) {
                                        saveDiaryEntry(currentEntryContent.toString())
                                    } else {
                                        resetToHotwordDetection()
                                    }
                                }
                            }, 1000)
                            return
                        } catch (e: Exception) {
                            "Recognition error, trying to recover"
                        }
                    }
                    else -> "Recognition error: $error"
                }

                Log.e("HotwordService", "Error recording diary entry: $error ($errorMessage)")
                updateNotification(errorMessage)

                if (currentEntryContent.isNotEmpty()) {
                    saveDiaryEntry(currentEntryContent.toString())
                } else {
                    resetToHotwordDetection()
                }
            }

            override fun onRmsChanged(rmsdB: Float) {
                if (isRecordingEntry) {
                    // Visual feedback of recording activity
                    val level = (rmsdB / 10).toInt().coerceIn(0, 5)
                    val indicator = "▌".repeat(level)
                    updateNotification("Recording: $indicator")
                }
            }

            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {} // We'll handle this through timeouts and errors
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        // Start listening for the entry
        try {
            speechRecognizer?.startListening(recordIntent)
        } catch (e: Exception) {
            Log.e("HotwordService", "Failed to start recording", e)
            // Retry after a delay
            handler.postDelayed({
                try {
                    speechRecognizer?.startListening(recordIntent)
                } catch (e: Exception) {
                    Log.e("HotwordService", "Second attempt to start recording failed", e)
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
