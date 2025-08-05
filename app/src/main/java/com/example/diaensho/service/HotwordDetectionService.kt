package com.example.diaensho.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresPermission
import com.example.diaensho.audio.AudioManager
import com.example.diaensho.audio.SpeechToTextManager
import com.example.diaensho.audio.WakeWordDetector
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
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest

@AndroidEntryPoint
class HotwordDetectionService : Service() {
    private var silenceTimeoutJob: Job? = null
    private var lastSpeechTime = 0L

    companion object {
        private const val TAG = "HotwordService"
        private const val END_PHRASE = "that's it"
        private const val DIARY_RECORDING_TIMEOUT_MS = 300000L // 5 minutes
        private const val SERVICE_RESTART_DELAY_MS = 5000L
        private const val SILENCE_TIMEOUT_MS = 10000L // 10 seconds of silence
    }

    @Inject lateinit var repository: MainRepository
    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var powerManagerHelper: PowerManagerHelper
    @Inject lateinit var audioManager: AudioManager
    @Inject lateinit var wakeWordDetector: WakeWordDetector
    @Inject lateinit var speechToTextManager: SpeechToTextManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentState = ServiceState.IDLE
    private var diaryContent = StringBuilder()
    private var recordingTimeoutJob: Job? = null
    private var restartJob: Job? = null

    enum class ServiceState {
        IDLE, LISTENING_FOR_WAKE_WORD, RECORDING_DIARY_ENTRY, PROCESSING, ERROR
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Service created")

        startForeground(
            NotificationHelper.HOTWORD_NOTIFICATION_ID,
            notificationHelper.createHotwordNotification("Initializing...")
        )

        powerManagerHelper.acquireWakeLock()
        startWakeWordDetection()
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun startWakeWordDetection() {
        if (currentState == ServiceState.LISTENING_FOR_WAKE_WORD) {
            Log.w(TAG, "Already listening for wake word")
            return
        }

        currentState = ServiceState.LISTENING_FOR_WAKE_WORD
        updateNotification("Listening for wake word...")

        val success = wakeWordDetector.startListening { detectedKeyword ->
            Log.i(TAG, "Wake word detected: $detectedKeyword")
            onWakeWordDetected()
        }

        if (!success) {
            Log.e(TAG, "Failed to start wake word detection")
            handleError("Failed to start wake word detection")
        } else {
            Log.i(TAG, "Wake word detection started successfully")
        }

        // Monitor wake word detector state
        serviceScope.launch {
            wakeWordDetector.detectionState.collectLatest { state ->
                when (state) {
                    WakeWordDetector.DetectionState.ERROR -> {
                        Log.e(TAG, "Wake word detector error")
                        handleError("Wake word detection error")
                    }
                    WakeWordDetector.DetectionState.LISTENING -> {
                        updateNotification("🎤 Listening for wake word...")
                    }
                    else -> { /* Handle other states if needed */ }
                }
            }
        }
    }

    private fun onWakeWordDetected() {
        wakeWordDetector.stopListening()
        startDiaryRecording()
    }

    private fun startDiaryRecording() {
        currentState = ServiceState.RECORDING_DIARY_ENTRY
        diaryContent.clear()
        lastSpeechTime = System.currentTimeMillis()

        updateNotification("📝 Recording diary entry... Say '$END_PHRASE' when done")

        // Cancel any existing timeouts
        recordingTimeoutJob?.cancel()
        silenceTimeoutJob?.cancel()

        // Set recording timeout
        recordingTimeoutJob = serviceScope.launch {
            delay(DIARY_RECORDING_TIMEOUT_MS)
            if (currentState == ServiceState.RECORDING_DIARY_ENTRY) {
                Log.i(TAG, "Diary recording timed out")
                finalizeDiaryEntry("Recording timed out after 5 minutes")
            }
        }

        // Start silence monitoring
        startSilenceMonitoring()

        // Start speech recognition on Main dispatcher
        serviceScope.launch {
            withContext(Dispatchers.Main) {
                delay(3000) // 3 second delay to allow user to prepare

                val success = speechToTextManager.startListening(
                    onResult = { result ->
                        handleSpeechResult(result)
                    },
                    onError = { error ->
                        Log.e(TAG, "Speech recognition error: $error")
                        if (diaryContent.isNotEmpty()) {
                            finalizeDiaryEntry("Recognition error, but saved content")
                        } else {
                            restartDiaryRecording()
                        }
                    },
                    onComplete = {
                        if (currentState == ServiceState.RECORDING_DIARY_ENTRY) {
                            if (diaryContent.isNotEmpty()) {
                                finalizeDiaryEntry("Recording completed")
                            } else {
                                restartDiaryRecording()
                            }
                        }
                    },
                    preferOffline = false,
                    maxResults = 5
                )

                if (!success) {
                    Log.e(TAG, "Failed to start speech recognition")
                    handleError("Failed to start speech recognition")
                }
            }
        }

        // Monitor speech recognition state for visual feedback
        serviceScope.launch {
            speechToTextManager.recognitionState.collectLatest { state ->
                when (state) {
                    SpeechToTextManager.RecognitionState.LISTENING -> {
                        updateNotification("🎤 Listening... Say '$END_PHRASE' when done")
                    }
                    SpeechToTextManager.RecognitionState.PROCESSING -> {
                        updateNotification("⚡ Processing speech...")
                    }
                    else -> { /* Other states handled in callbacks */ }
                }
            }
        }

        // Monitor audio level for visual feedback
        serviceScope.launch {
            speechToTextManager.confidence.collectLatest { level ->
                if (currentState == ServiceState.RECORDING_DIARY_ENTRY && level > 0.1f) {
                    val indicator = "▌".repeat((level * 5).toInt().coerceIn(1, 5))
                    updateNotification("🎤 Recording $indicator Say '$END_PHRASE' when done")
                }
            }
        }
    }

    private fun startSilenceMonitoring() {
        silenceTimeoutJob?.cancel()
        silenceTimeoutJob = serviceScope.launch {
            while (currentState == ServiceState.RECORDING_DIARY_ENTRY) {
                delay(1000) // Check every second

                val timeSinceLastSpeech = System.currentTimeMillis() - lastSpeechTime
                if (timeSinceLastSpeech > SILENCE_TIMEOUT_MS) {
                    Log.i(TAG, "Silence timeout reached")
                    if (currentState == ServiceState.RECORDING_DIARY_ENTRY) {
                        finalizeDiaryEntry("Silence timeout after 10 seconds")
                        break
                    }
                }
            }
        }
    }

    private fun handleSpeechResult(result: SpeechToTextManager.RecognitionResult) {
        val text = result.text.trim()

        if (text.isEmpty()) return

        Log.d(TAG, "Speech result: '$text' (partial: ${result.isPartial}, confidence: ${result.confidence})")

        // Update last speech time
        lastSpeechTime = System.currentTimeMillis()

        // Check for end phrase
        if (text.lowercase().contains(END_PHRASE.lowercase())) {
            Log.i(TAG, "End phrase detected in: '$text'")

            // Remove end phrase and finalize
            val contentWithoutEndPhrase = text
                .replace(END_PHRASE, "", ignoreCase = true)
                .trim()

            if (contentWithoutEndPhrase.isNotEmpty()) {
                if (diaryContent.isNotEmpty()) diaryContent.append(" ")
                diaryContent.append(contentWithoutEndPhrase)
            }

            finalizeDiaryEntry("End phrase detected")
            return
        }

        // MODIFIED: Always accumulate speech content, regardless of whether it's partial or final
        // This ensures we capture content even if we only get partial results
        if (text.length > 3) { // Only accumulate if text is meaningful (more than 3 characters)
            // Update diary content with the latest speech
            if (result.isPartial) {
                // For partial results, update notification but also keep the content
                updateNotification("📝 \"$text...\" Say '$END_PHRASE' when done")

                // Update the current content (we'll use the latest partial result)
                diaryContent.clear()
                diaryContent.append(text)
            } else {
                // For final results, definitely accumulate the content
                if (diaryContent.isNotEmpty()) {
                    diaryContent.append(" ")
                }
                diaryContent.append(text)

                Log.i(TAG, "Final speech result added to diary: '$text'")
                Log.d(TAG, "Current diary content: '${diaryContent}'")

                updateNotification("📝 Recorded: \"${text}\" Continue or say '$END_PHRASE'")
            }
        }
    }

    private fun finalizeDiaryEntry(reason: String) {
        // Prevent multiple simultaneous finalization attempts
        if (currentState != ServiceState.RECORDING_DIARY_ENTRY) {
            Log.w(TAG, "Ignoring finalization attempt - not in recording state")
            return
        }

        currentState = ServiceState.PROCESSING

        // Cancel all timeouts and stop listening on main thread
        recordingTimeoutJob?.cancel()
        silenceTimeoutJob?.cancel()

        serviceScope.launch {
            withContext(Dispatchers.Main) {
                try {
                    speechToTextManager.stopListening()
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping speech recognition", e)
                }
            }
        }

        val finalContent = diaryContent.toString().trim()

        Log.i(TAG, "Finalizing diary entry. Reason: $reason, Content: '$finalContent'")

        if (finalContent.isNotEmpty()) {
            updateNotification("💾 Saving diary entry...")

            serviceScope.launch {
                try {
                    repository.addDiaryEntry(finalContent)
                    Log.i(TAG, "Diary entry saved successfully: '$finalContent'")
                    updateNotification("✅ Entry saved: \"${finalContent.take(50)}${if (finalContent.length > 50) "..." else ""}\"")

                    // Show success for a moment before restarting
                    delay(3000)
                    restartWakeWordDetection()

                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save diary entry", e)
                    updateNotification("❌ Failed to save entry")
                    delay(2000)
                    handleError("Failed to save diary entry: ${e.message}")
                }
            }
        } else {
            Log.w(TAG, "No content to save")
            updateNotification("⚠️ No content recorded")
            serviceScope.launch {
                delay(2000)
                restartWakeWordDetection()
            }
        }
    }

    private fun handleError(message: String) {
        // Prevent multiple simultaneous error handling
        if (currentState == ServiceState.ERROR) {
            Log.w(TAG, "Already in error state, ignoring: $message")
            return
        }

        Log.e(TAG, "Service error: $message")
        currentState = ServiceState.ERROR

        // Stop all ongoing operations
        recordingTimeoutJob?.cancel()
        silenceTimeoutJob?.cancel()
        wakeWordDetector.stopListening()

        serviceScope.launch {
            withContext(Dispatchers.Main) {
                try {
                    speechToTextManager.stopListening()
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping speech recognition during error handling", e)
                }
            }
        }

        updateNotification("❌ Error: $message")

        // Schedule service restart
        restartJob?.cancel()
        restartJob = serviceScope.launch {
            delay(SERVICE_RESTART_DELAY_MS)
            Log.i(TAG, "Restarting service after error")
            restartWakeWordDetection()
        }
    }

    @SuppressLint("MissingPermission")
    private fun restartWakeWordDetection() {
        Log.i(TAG, "Restarting wake word detection")

        // Reset state
        currentState = ServiceState.IDLE
        diaryContent.clear()
        recordingTimeoutJob?.cancel()
        restartJob?.cancel()
        silenceTimeoutJob?.cancel()

        // Small delay to ensure clean restart
        serviceScope.launch {
            delay(1000)
            startWakeWordDetection()
        }
    }

    private fun restartDiaryRecording() {
        Log.i(TAG, "Restarting diary recording due to no speech detected")
        updateNotification("⚠️ No speech detected, trying again... Speak now!")

        serviceScope.launch {
            withContext(Dispatchers.Main) {
                delay(2000) // 2 second delay before retry

                val success = speechToTextManager.startListening(
                    onResult = { result ->
                        handleSpeechResult(result)
                    },
                    onError = { error ->
                        Log.e(TAG, "Speech recognition retry error: $error")
                        handleError("Speech recognition failed after retry: $error")
                    },
                    onComplete = {
                        if (currentState == ServiceState.RECORDING_DIARY_ENTRY) {
                            if (diaryContent.isNotEmpty()) {
                                finalizeDiaryEntry("Recording completed")
                            } else {
                                Log.w(TAG, "No content captured after retry")
                                updateNotification("⚠️ No content recorded")
                                serviceScope.launch {
                                    delay(2000)
                                    restartWakeWordDetection()
                                }
                            }
                        }
                    },
                    preferOffline = false,
                    maxResults = 5
                )

                if (!success) {
                    handleError("Failed to restart speech recognition")
                }
            }
        }
    }

    private fun updateNotification(message: String) {
        notificationHelper.updateHotwordNotification(message)
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "Service start command received")

        // Ensure we're listening if not already
        if (currentState == ServiceState.IDLE) {
            startWakeWordDetection()
        }

        return START_STICKY // Restart if killed
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "Service destroyed")

        // Clean up all resources
        recordingTimeoutJob?.cancel()
        restartJob?.cancel()
        silenceTimeoutJob?.cancel()

        wakeWordDetector.release()
        speechToTextManager.release()
        audioManager.release()
        powerManagerHelper.releaseWakeLock()

        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
