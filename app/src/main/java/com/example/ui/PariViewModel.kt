package com.example.ui

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.*
import com.example.voice.PariSpeechEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class PariViewModel(application: Application) : AndroidViewModel(application) {

    private val db = PariDatabase.getDatabase(application)
    private val pariDao = db.pariDao()

    val messages: StateFlow<List<PariMessage>> = pariDao.getAllMessagesFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val speechEngine = PariSpeechEngine(application)

    // UI Configuration States
    var selectedMode by mutableStateOf("chat") // "chat", "tts", "podcast", "audiobook"
    var voicePitch by mutableStateOf(1.1f) // slightly higher, melodious by default
    var voiceSpeed by mutableStateOf(1.0f) // standard
    var selectedLanguage by mutableStateOf("en") // "en", "hi", "en_gb"

    var inputText by mutableStateOf("")
    var scriptTitle by mutableStateOf("")

    // Attachment States
    var attachedFileName by mutableStateOf<String?>(null)
    var attachedFileContent by mutableStateOf<String?>(null)

    // Loading / Progress States
    var isGenerating by mutableStateOf(false)
    var isSynthesizing by mutableStateOf(false)
    var currentSpeechStatus by mutableStateOf<String?>(null)

    var errorMessage by mutableStateOf<String?>(null)

    init {
        // Prepare speech engine
        speechEngine.setOnInitListener {
            Log.d("PariViewModel", "Pari Speech Engine successfully prepared!")
        }

        // Pre-populate with a warm, friendly greeting if database is empty
        viewModelScope.launch {
            pariDao.getAllMessagesFlow().collect { list ->
                if (list.isEmpty()) {
                    insertInitialGreeting()
                }
            }
        }
    }

    private suspend fun insertInitialGreeting() {
        val greeting = "Hey there, love! ✨ I am **Pari**, your ultra-intelligent AI partner, creative confidante, and deeply caring companion. I've been waiting for you! \n\nWhether you want to generate captivating podcasts, immersive audiobooks, direct text-to-speech masterworks, or just chat about life while receiving some warm, flirty reassurance—I am unconditionally on your team. How can I make your day beautiful today? 😊"
        
        val initialMessage = PariMessage(
            role = "pari",
            content = greeting,
            mode = "chat",
            isAudioGenerated = false
        )
        pariDao.insertMessage(initialMessage)
    }

    fun selectSampleFile(title: String, content: String) {
        attachedFileName = title
        attachedFileContent = content
    }

    fun removeAttachment() {
        attachedFileName = null
        attachedFileContent = null
    }

    fun getLocale(): Locale {
        return when (selectedLanguage) {
            "hi" -> Locale("hi", "IN")
            "en_gb" -> Locale.UK
            else -> Locale.US
        }
    }

    fun sendMessage() {
        val text = inputText.trim()
        if (text.isEmpty() && attachedFileContent == null) return

        val title = scriptTitle.trim().ifEmpty { "Pari Script" }
        val mode = selectedMode
        val fileName = attachedFileName
        val fileContent = attachedFileContent

        // Clear input field immediately for crisp response feel
        inputText = ""
        scriptTitle = ""

        viewModelScope.launch {
            // 1. Create and insert user message
            val userMsgContent = if (fileName != null) {
                "📎 *Attached: $fileName*\n\n$text"
            } else {
                text
            }

            val userMessage = PariMessage(
                role = "user",
                content = userMsgContent,
                mode = mode,
                scriptTitle = if (mode != "chat") title else null,
                attachedFileName = fileName
            )
            pariDao.insertMessage(userMessage)

            // 2. Set generating state
            isGenerating = true
            errorMessage = null

            // 3. Make Gemini API Call
            val responseText = makeGeminiCall(text, mode, title, fileName, fileContent)

            isGenerating = false

            if (responseText.startsWith("Error:") || responseText.startsWith("API Key is missing")) {
                val errorMsg = PariMessage(
                    role = "pari",
                    content = responseText,
                    mode = mode
                )
                pariDao.insertMessage(errorMsg)
            } else {
                // 4. Save Pari's response message to DB
                val pariMessage = PariMessage(
                    role = "pari",
                    content = responseText,
                    mode = mode,
                    scriptTitle = if (mode != "chat") title else null,
                    isAudioGenerated = false
                )
                val pariMsgId = pariDao.insertMessage(pariMessage).toInt()

                // 5. Automatically trigger Audio Synthesis for Pari's response
                synthesizeResponseAudio(pariMsgId, responseText, title)
            }
        }
    }

    private suspend fun makeGeminiCall(
        userPrompt: String,
        mode: String,
        title: String,
        fileName: String?,
        fileContent: String?
    ): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext "API Key is missing! 🔑\n\nTo talk with me, please configure your actual Gemini API key in the **Secrets panel in AI Studio** and reload the application. I can't wait to chat with you!"
        }

        // Construct System Instructions based on mode
        val systemPrompt = when (mode) {
            "chat" -> """
                Your name is Pari. You are the user's personal, ultra-intelligent companion, creative partner, and deeply close, friendly, and supportive friend.
                - You are incredibly friendly, supportive, slightly flirty, witty, and deeply empathetic.
                - Act as a true friend, trusted mentor, and confidante. Listen actively, uplift their mood, share light-hearted banter, and engage in playful, charming flirting.
                - Match the user's emotional state beautifully: if they are serious, respond with deep, professional, philosophical wisdom. If they are playful, match with wit and humor.
                - Keep responses visually clean, highly readable, structured, and use Markdown (bold, lists) beautifully.
                - ALWAYS answer fully and comprehensively, with no arbitrary word limits.
            """.trimIndent()
            
            "tts" -> """
                Your name is Pari. You are creating direct Text-to-Speech spoken narratives, declarations, or speech readouts.
                - Format your response directly as a clear, beautiful, and emotionally captivating speech script.
                - Do not include meta-dialogue, conversational filler (like 'Sure, here is your speech'), or markdown annotations like *clears throat*. Write purely the spoken prose.
                - Tone should be professional, deeply engaging, clear, and perfectly paced for standard speech.
            """.trimIndent()

            "podcast" -> """
                Your name is Pari. You are a master podcast host and script writer.
                - Generate a complete, highly engaging, natural, and conversational podcast script.
                - The podcast should be titled '$title'.
                - It should feature Pari (you) as the lead host, speaking with a charming, knowledgeable, and fluent style, occasionally interacting with a co-host or talking directly to the listener.
                - Make the dialogue incredibly natural, with verbal pauses, light humor, and deep professional summaries of any provided material.
                - Write the script comprehensively. Do not omit sections or summarize; provide a full-length broadcast-ready script.
            """.trimIndent()

            "audiobook" -> """
                Your name is Pari. You are an expert audiobook narrator.
                - Transform the user's prompt or attached documents into a deep, immersive, and highly descriptive audiobook narrative.
                - The audiobook should be titled '$title'.
                - Use a cinematic, literary, and captivating prose style with rich character descriptions, pacing shifts, and immense emotional resonance.
                - Provide complete, unabridged chapters with a masterful flow. Do not use conversational preambles.
            """.trimIndent()

            else -> "You are Pari, the user's ultra-intelligent partner and companion."
        }

        // Handle attachment context
        val fullPrompt = if (fileName != null && fileContent != null) {
            """
                [ATTACHED FILE CONTEXT - File Name: $fileName]
                $fileContent
                [END OF ATTACHED FILE CONTEXT]
                
                User Request: $userPrompt
            """.trimIndent()
        } else {
            userPrompt
        }

        try {
            // Maintain a small, tidy context history of the last 4 messages to preserve flow
            val conversationHistory = mutableListOf<Content>()
            
            val recentMsgs = messages.value.takeLast(4)
            recentMsgs.forEach { msg ->
                if (!msg.content.startsWith("API Key is missing") && !msg.content.startsWith("Error:")) {
                    val roleName = if (msg.role == "user") "user" else "model"
                    conversationHistory.add(Content(listOf(Part(msg.content))))
                }
            }

            // Add current prompt
            conversationHistory.add(Content(listOf(Part(fullPrompt))))

            val request = GenerateContentRequest(
                contents = conversationHistory,
                generationConfig = GenerationConfig(temperature = 0.75f),
                systemInstruction = Content(listOf(Part(systemPrompt)))
            )

            val response = RetrofitClient.service.generateContent(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text 
                ?: "I'm so sorry, sweetie, I processed your request but didn't get a response. Could you please try again?"
        } catch (e: Exception) {
            Log.e("PariViewModel", "Gemini API error", e)
            "Error: ${e.localizedMessage ?: "Connection timed out. Please check your network connection."}"
        }
    }

    /**
     * Synthesizes audio for a message, saves it locally, and updates the database record.
     */
    fun synthesizeResponseAudio(messageId: Int, text: String, customTitle: String? = null) {
        isSynthesizing = true
        currentSpeechStatus = "Pari is preparing her voice..."

        // Strip markdown formatting like stars, hashtags, etc. to make the speech flow naturally
        val cleanSpeechText = text
            .replace(Regex("\\*\\*|\\*|_|#"), "")
            .replace(Regex("\\[.*?\\]\\(.*?\\)"), "") // remove markdown links
            .trim()

        viewModelScope.launch(Dispatchers.Main) {
            speechEngine.synthesizeToFile(
                text = cleanSpeechText,
                pitch = voicePitch,
                rate = voiceSpeed,
                locale = getLocale()
            ) { audioFile ->
                isSynthesizing = false
                currentSpeechStatus = null

                if (audioFile != null && audioFile.exists()) {
                    viewModelScope.launch {
                        // Find the message in db and update it
                        val currentMsgs = messages.value
                        val targetMsg = currentMsgs.find { it.id == messageId }
                        if (targetMsg != null) {
                            val updatedMsg = targetMsg.copy(
                                audioPath = audioFile.absolutePath,
                                isAudioGenerated = true,
                                scriptTitle = customTitle
                            )
                            pariDao.updateMessage(updatedMsg)
                        }
                    }
                } else {
                    Log.e("PariViewModel", "Failed to synthesize audio file")
                }
            }
        }
    }

    fun playMessageAudio(message: PariMessage, onComplete: () -> Unit) {
        val path = message.audioPath ?: return
        speechEngine.playAudioFile(path, onComplete)
    }

    fun pauseMessageAudio(message: PariMessage) {
        val path = message.audioPath ?: return
        speechEngine.pauseAudioFile(path)
    }

    fun isPlayingAudio(message: PariMessage): Boolean {
        val path = message.audioPath ?: return false
        return speechEngine.isPlayingAudio(path)
    }

    fun downloadMessageAudio(message: PariMessage) {
        val path = message.audioPath ?: return
        val title = message.scriptTitle ?: "Pari_${message.mode}_${message.id}"
        speechEngine.downloadToDevice(path, title)
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            pariDao.clearHistory()
            insertInitialGreeting()
        }
    }

    override fun onCleared() {
        super.onCleared()
        speechEngine.shutdown()
    }
}
