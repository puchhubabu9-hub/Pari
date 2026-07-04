package com.example.voice

import android.content.ContentValues
import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.widget.Toast
import java.io.File
import java.util.Locale
import java.util.UUID

class PariSpeechEngine(private val context: Context) : TextToSpeech.OnInitListener {

    private val attributionContext: Context = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        context.createAttributionContext("pari")
    } else {
        context
    }

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var onInitCallback: (() -> Unit)? = null
    private val activePlayers = mutableMapOf<String, MediaPlayer>()

    init {
        tts = TextToSpeech(attributionContext.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.let { t ->
                t.language = Locale.US
                isInitialized = true
                Log.d("PariSpeechEngine", "TTS successfully initialized")
                onInitCallback?.invoke()

                t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        Log.d("PariSpeechEngine", "Utterance started: $utteranceId")
                    }

                    override fun onDone(utteranceId: String?) {
                        Log.d("PariSpeechEngine", "Utterance done: $utteranceId")
                        // Fire complete callback if mapped
                        utteranceId?.let { id ->
                            val callback = fileSynthesisCallbacks[id]
                            if (callback != null) {
                                val file = fileSynthesisDestinations[id]
                                callback(file)
                                fileSynthesisCallbacks.remove(id)
                                fileSynthesisDestinations.remove(id)
                            }
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        Log.e("PariSpeechEngine", "Utterance error: $utteranceId")
                        utteranceId?.let { id ->
                            val callback = fileSynthesisCallbacks[id]
                            callback?.invoke(null)
                            fileSynthesisCallbacks.remove(id)
                            fileSynthesisDestinations.remove(id)
                        }
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        Log.e("PariSpeechEngine", "Utterance error $errorCode: $utteranceId")
                        utteranceId?.let { id ->
                            val callback = fileSynthesisCallbacks[id]
                            callback?.invoke(null)
                            fileSynthesisCallbacks.remove(id)
                            fileSynthesisDestinations.remove(id)
                        }
                    }
                })
            }
        } else {
            Log.e("PariSpeechEngine", "Failed to initialize TTS")
        }
    }

    private val fileSynthesisCallbacks = mutableMapOf<String, (File?) -> Unit>()
    private val fileSynthesisDestinations = mutableMapOf<String, File>()

    fun setOnInitListener(callback: () -> Unit) {
        if (isInitialized) {
            callback()
        } else {
            onInitCallback = callback
        }
    }

    fun speak(
        text: String,
        pitch: Float = 1.0f,
        rate: Float = 1.0f,
        locale: Locale = Locale.US
    ) {
        if (!isInitialized) {
            Log.e("PariSpeechEngine", "TTS is not initialized yet")
            return
        }
        tts?.apply {
            setPitch(pitch)
            setSpeechRate(rate)
            language = locale
            speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
        }
    }

    fun stopSpeaking() {
        if (isInitialized) {
            tts?.stop()
        }
        activePlayers.values.forEach { player ->
            try {
                if (player.isPlaying) {
                    player.stop()
                }
                player.release()
            } catch (e: Exception) {
                // ignore
            }
        }
        activePlayers.clear()
    }

    /**
     * Synthesizes text to a local audio file (.wav format) and triggers a callback when done.
     */
    fun synthesizeToFile(
        text: String,
        pitch: Float = 1.0f,
        rate: Float = 1.0f,
        locale: Locale = Locale.US,
        onComplete: (File?) -> Unit
    ) {
        if (!isInitialized) {
            Log.e("PariSpeechEngine", "TTS is not initialized yet")
            onComplete(null)
            return
        }

        val utteranceId = UUID.randomUUID().toString()
        val tempFile = File(attributionContext.cacheDir, "pari_audio_$utteranceId.wav")

        tts?.apply {
            setPitch(pitch)
            setSpeechRate(rate)
            language = locale

            fileSynthesisCallbacks[utteranceId] = onComplete
            fileSynthesisDestinations[utteranceId] = tempFile

            val result = synthesizeToFile(text, null, tempFile, utteranceId)
            if (result != TextToSpeech.SUCCESS) {
                Log.e("PariSpeechEngine", "synthesizeToFile returned failure code: $result")
                fileSynthesisCallbacks.remove(utteranceId)
                fileSynthesisDestinations.remove(utteranceId)
                onComplete(null)
            }
        }
    }

    /**
     * Plays a synthesized audio file using MediaPlayer.
     */
    fun playAudioFile(filePath: String, onCompletionListener: () -> Unit) {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                Log.e("PariSpeechEngine", "Audio file does not exist at $filePath")
                return
            }

            // Stop any existing player for this file
            activePlayers[filePath]?.let { player ->
                if (player.isPlaying) player.stop()
                player.release()
            }

            val player = MediaPlayer().apply {
                setDataSource(filePath)
                prepare()
                start()
                setOnCompletionListener {
                    it.release()
                    activePlayers.remove(filePath)
                    onCompletionListener()
                }
            }
            activePlayers[filePath] = player
        } catch (e: Exception) {
            Log.e("PariSpeechEngine", "Error playing audio file: ${e.message}", e)
        }
    }

    fun isPlayingAudio(filePath: String): Boolean {
        return activePlayers[filePath]?.isPlaying == true
    }

    fun pauseAudioFile(filePath: String) {
        activePlayers[filePath]?.let { player ->
            if (player.isPlaying) {
                player.pause()
            }
        }
    }

    fun resumeAudioFile(filePath: String) {
        activePlayers[filePath]?.let { player ->
            if (!player.isPlaying) {
                player.start()
            }
        }
    }

    /**
     * Saves the audio file to the device's shared Downloads folder and displays a Toast confirmation.
     */
    fun downloadToDevice(filePath: String, customTitle: String? = null) {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                Toast.makeText(attributionContext, "Audio file not generated yet!", Toast.LENGTH_SHORT).show()
                return
            }

            val title = customTitle ?: "Pari_Audio_${System.currentTimeMillis()}"
            val finalFileName = "$title.wav"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = attributionContext.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, finalFileName)
                    put(MediaStore.Downloads.MIME_TYPE, "audio/wav")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Pari")
                }

                val uri: Uri? = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { outputStream ->
                        file.inputStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    Toast.makeText(attributionContext, "Saved to Downloads/Pari/$finalFileName!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(attributionContext, "Failed to download audio", Toast.LENGTH_SHORT).show()
                }
            } else {
                // Fallback for older versions
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val pariDir = File(downloadsDir, "Pari").apply { mkdirs() }
                val destFile = File(pariDir, finalFileName)
                file.inputStream().use { inputStream ->
                    destFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                Toast.makeText(attributionContext, "Saved to Downloads/Pari/$finalFileName!", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e("PariSpeechEngine", "Failed to download audio file: ${e.message}", e)
            Toast.makeText(context, "Error saving audio: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
        isInitialized = false
        stopSpeaking()
    }
}
