package com.example.ui.viewmodel

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.ai.GeminiAiService
import com.example.ai.StreamChunkEvent
import com.example.data.model.AssistantLanguage
import com.example.data.model.AssistantMode
import com.example.data.model.ChatMessage
import com.example.data.model.ChatThread
import com.example.data.model.SourceLink
import com.example.data.model.UserProfile
import com.example.data.preferences.AppPreferencesRepository
import com.example.data.preferences.LocalAppSettings
import com.example.data.repository.ChatRepository
import com.example.media.ImraxVoiceAssistant
import com.example.media.PendingAttachment
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "ImraxViewModel"

enum class AppDestination {
    CHAT,
    EXPLORE,
    SETTINGS
}

data class ImraxUiState(
    val activeDestination: AppDestination = AppDestination.CHAT,
    val activeChatId: String? = null,
    val selectedMode: AssistantMode = AssistantMode.CHAT,
    val selectedLanguage: AssistantLanguage = AssistantLanguage.ENGLISH,
    val inputText: String = "",
    val searchQuery: String = "",
    val pendingAttachment: PendingAttachment? = null,
    val isGenerating: Boolean = false,
    val isGeneratingImage: Boolean = false,
    val streamingResponse: String = "",
    val streamingSources: List<SourceLink> = emptyList(),
    val errorBanner: String? = null,
    val editingMessage: ChatMessage? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class ImraxViewModel(
    private val currentUserId: String,
    userDisplayName: String,
    userEmail: String?,
    userPhotoUrl: String?,
    private val chatRepository: ChatRepository,
    private val aiService: GeminiAiService,
    private val prefsRepository: AppPreferencesRepository,
    val voiceAssistant: ImraxVoiceAssistant
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImraxUiState())
    val uiState: StateFlow<ImraxUiState> = _uiState.asStateFlow()

    private val _generatedBitmaps = MutableStateFlow<Map<String, Bitmap>>(emptyMap())
    val generatedBitmaps: StateFlow<Map<String, Bitmap>> = _generatedBitmaps.asStateFlow()

    private val _attachmentPreviewBitmaps = MutableStateFlow<Map<String, Bitmap>>(emptyMap())
    val attachmentPreviewBitmaps: StateFlow<Map<String, Bitmap>> = _attachmentPreviewBitmaps.asStateFlow()

    private var activeGenerationJob: Job? = null

    val localSettings: StateFlow<LocalAppSettings> = prefsRepository.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = LocalAppSettings()
        )

    val userProfile: StateFlow<UserProfile?> = chatRepository.observeUserProfile(currentUserId)
        .catch { error ->
            unwrapFirestoreError(error, "profile")
            emit(null)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = null
        )

    val chatThreads: StateFlow<List<ChatThread>> = chatRepository.observeChats(currentUserId)
        .catch { error ->
            val msg = unwrapFirestoreError(error, "chats")
            _uiState.update { it.copy(errorBanner = msg) }
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val activeMessages: StateFlow<List<ChatMessage>> = _uiState
        .map { it.activeChatId }
        .flatMapLatest { chatId ->
            if (chatId.isNullOrBlank()) {
                flowOf(emptyList())
            } else {
                chatRepository.observeMessages(currentUserId, chatId)
                    .catch { error ->
                        val msg = unwrapFirestoreError(error, "messages")
                        _uiState.update { it.copy(errorBanner = msg) }
                        emit(emptyList())
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    init {
        viewModelScope.launch {
            chatRepository.ensureUserProfile(
                userId = currentUserId,
                displayName = userDisplayName,
                email = userEmail,
                photoUrl = userPhotoUrl
            ).onFailure { error ->
                Log.w(TAG, "Profile sync warning: ${error.message}")
            }
        }
        viewModelScope.launch {
            prefsRepository.settingsFlow.collect { settings ->
                _uiState.update { it.copy(selectedLanguage = settings.preferredLanguage) }
            }
        }
    }

    fun navigateTo(destination: AppDestination) {
        _uiState.update { it.copy(activeDestination = destination, errorBanner = null) }
    }

    fun startNewChat(mode: AssistantMode = AssistantMode.CHAT) {
        activeGenerationJob?.cancel()
        voiceAssistant.stopSpeaking()
        _uiState.update {
            it.copy(
                activeDestination = AppDestination.CHAT,
                activeChatId = null,
                selectedMode = mode,
                inputText = "",
                pendingAttachment = null,
                isGenerating = false,
                isGeneratingImage = false,
                streamingResponse = "",
                streamingSources = emptyList(),
                errorBanner = null,
                editingMessage = null
            )
        }
    }

    fun selectChat(thread: ChatThread) {
        activeGenerationJob?.cancel()
        voiceAssistant.stopSpeaking()
        _uiState.update {
            it.copy(
                activeDestination = AppDestination.CHAT,
                activeChatId = thread.id,
                selectedMode = AssistantMode.fromId(thread.mode),
                selectedLanguage = AssistantLanguage.fromCode(thread.language),
                isGenerating = false,
                isGeneratingImage = false,
                streamingResponse = "",
                streamingSources = emptyList(),
                errorBanner = null
            )
        }
    }

    fun updateInputText(value: String) {
        _uiState.update { it.copy(inputText = value) }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun selectMode(mode: AssistantMode) {
        _uiState.update { it.copy(selectedMode = mode, errorBanner = null) }
    }

    fun selectLanguage(language: AssistantLanguage) {
        _uiState.update { it.copy(selectedLanguage = language) }
        viewModelScope.launch {
            prefsRepository.setPreferredLanguage(language)
            chatRepository.updateUserPreferences(
                userId = currentUserId,
                preferredLanguage = language.code
            )
        }
    }

    fun toggleTheme(darkTheme: Boolean) {
        viewModelScope.launch {
            prefsRepository.setDarkTheme(darkTheme)
            chatRepository.updateUserPreferences(
                userId = currentUserId,
                darkTheme = darkTheme
            )
        }
    }

    fun toggleAutoSpeak(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepository.setAutoSpeakResponses(enabled)
        }
    }

    fun attachMedia(attachment: PendingAttachment) {
        val suggestedMode = if (_uiState.value.selectedMode == AssistantMode.IMAGE) {
            AssistantMode.IMAGE
        } else if (!attachment.isImage) {
            AssistantMode.DOCUMENT
        } else {
            _uiState.value.selectedMode
        }
        _uiState.update {
            it.copy(
                pendingAttachment = attachment,
                selectedMode = suggestedMode,
                errorBanner = null
            )
        }
    }

    fun clearAttachment() {
        _uiState.update { it.copy(pendingAttachment = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorBanner = null) }
    }

    fun startVoiceListening() {
        val lang = _uiState.value.selectedLanguage
        voiceAssistant.startListening(
            language = lang,
            onPartialResult = { partial ->
                _uiState.update { it.copy(inputText = partial) }
            },
            onFinalResult = { finalText ->
                _uiState.update { it.copy(inputText = finalText) }
            },
            onError = { err ->
                _uiState.update { it.copy(errorBanner = err) }
            }
        )
    }

    fun stopVoiceListening() {
        voiceAssistant.stopListening()
    }

    fun toggleSpeakMessage(message: ChatMessage) {
        if (voiceAssistant.speakingMessageId.value == message.id) {
            voiceAssistant.stopSpeaking()
        } else {
            voiceAssistant.speak(message.id, message.content, _uiState.value.selectedLanguage)
        }
    }

    fun stopGeneration() {
        activeGenerationJob?.cancel()
        _uiState.update {
            it.copy(
                isGenerating = false,
                isGeneratingImage = false,
                streamingResponse = "",
                streamingSources = emptyList()
            )
        }
    }

    fun sendPrompt(overridePrompt: String? = null) {
        val state = _uiState.value
        if (state.isGenerating) return

        val prompt = (overridePrompt ?: state.inputText).trim()
        val attachment = state.pendingAttachment
        if (prompt.isEmpty() && attachment == null) return

        val mode = state.selectedMode
        val language = state.selectedLanguage
        val attachmentType = when {
            attachment == null -> "none"
            attachment.isImage -> "image"
            attachment.mimeType == "application/pdf" -> "pdf"
            else -> "document"
        }
        val effectiveDisplayPrompt = prompt.ifBlank {
            if (attachment?.isImage == true) "Analyze this image"
            else "Analyze ${attachment?.displayName ?: "this document"}"
        }

        _uiState.update {
            it.copy(
                inputText = "",
                pendingAttachment = null,
                isGenerating = true,
                isGeneratingImage = (mode == AssistantMode.IMAGE),
                streamingResponse = "",
                streamingSources = emptyList(),
                errorBanner = null
            )
        }

        activeGenerationJob = viewModelScope.launch {
            try {
                val existingHistory = activeMessages.value
                val chatId = state.activeChatId ?: run {
                    val title = effectiveDisplayPrompt.take(56)
                    val (newChatId, firstMsgId) = chatRepository.createChatWithFirstMessage(
                        userId = currentUserId,
                        title = title,
                        mode = mode.id,
                        language = language.code,
                        userContent = effectiveDisplayPrompt,
                        attachmentType = attachmentType,
                        attachmentName = attachment?.displayName
                    ).getOrThrow()
                    attachment?.bitmap?.let { bmp ->
                        _attachmentPreviewBitmaps.update { it + (firstMsgId to bmp) }
                    }
                    _uiState.update { it.copy(activeChatId = newChatId) }
                    newChatId
                }

                if (state.activeChatId != null) {
                    val userMsgId = chatRepository.addMessage(
                        userId = currentUserId,
                        chatId = chatId,
                        role = "user",
                        content = effectiveDisplayPrompt,
                        mode = mode.id,
                        attachmentType = attachmentType,
                        attachmentName = attachment?.displayName
                    ).getOrThrow()
                    attachment?.bitmap?.let { bmp ->
                        _attachmentPreviewBitmaps.update { it + (userMsgId to bmp) }
                    }
                }

                if (mode == AssistantMode.IMAGE) {
                    val generatedBmp = aiService.generateImage(
                        prompt = effectiveDisplayPrompt,
                        inputBitmap = attachment?.bitmap
                    )
                    val modelMsgId = chatRepository.addMessage(
                        userId = currentUserId,
                        chatId = chatId,
                        role = "model",
                        content = "Generated image for: \"$effectiveDisplayPrompt\"",
                        mode = mode.id,
                        attachmentType = "generated_image",
                        attachmentName = "IMRAX_AI_Image.png"
                    ).getOrThrow()
                    _generatedBitmaps.update { it + (modelMsgId to generatedBmp) }
                } else {
                    var finalContent = ""
                    var finalSources: List<SourceLink> = emptyList()

                    aiService.streamAssistantResponse(
                        prompt = effectiveDisplayPrompt,
                        history = existingHistory,
                        mode = mode,
                        language = language,
                        attachment = attachment
                    ).collect { event ->
                        when (event) {
                            is StreamChunkEvent.Delta -> {
                                _uiState.update {
                                    it.copy(
                                        streamingResponse = event.accumulatedText,
                                        streamingSources = event.sources
                                    )
                                }
                            }
                            is StreamChunkEvent.Completed -> {
                                finalContent = event.finalText
                                finalSources = event.sources
                            }
                        }
                    }

                    val modelMsgId = chatRepository.addMessage(
                        userId = currentUserId,
                        chatId = chatId,
                        role = "model",
                        content = finalContent,
                        mode = mode.id,
                        sources = finalSources.map { it.serialize() }
                    ).getOrThrow()

                    if (localSettings.value.autoSpeakResponses) {
                        voiceAssistant.speak(modelMsgId, finalContent, language)
                    }
                }

                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        isGeneratingImage = false,
                        streamingResponse = "",
                        streamingSources = emptyList()
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Prompt execution failed", e)
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        isGeneratingImage = false,
                        streamingResponse = "",
                        streamingSources = emptyList(),
                        errorBanner = e.localizedMessage ?: "Unable to complete request. Please try again."
                    )
                }
            }
        }
    }

    fun startEditingMessage(message: ChatMessage) {
        _uiState.update { it.copy(editingMessage = message) }
    }

    fun dismissEditingMessage() {
        _uiState.update { it.copy(editingMessage = null) }
    }

    fun submitEditedUserMessage(message: ChatMessage, updatedPrompt: String) {
        val trimmed = updatedPrompt.trim()
        val chatId = _uiState.value.activeChatId ?: return
        if (trimmed.isEmpty() || _uiState.value.isGenerating) return

        _uiState.update {
            it.copy(
                editingMessage = null,
                isGenerating = true,
                isGeneratingImage = false,
                streamingResponse = "",
                streamingSources = emptyList(),
                errorBanner = null
            )
        }

        activeGenerationJob = viewModelScope.launch {
            try {
                chatRepository.updateMessageContent(
                    userId = currentUserId,
                    chatId = chatId,
                    messageId = message.id,
                    newContent = trimmed
                ).getOrThrow()

                val currentList = activeMessages.value
                val msgIndex = currentList.indexOfFirst { it.id == message.id }
                val nextModelMsg = if (msgIndex >= 0 && msgIndex + 1 < currentList.size && currentList[msgIndex + 1].role == "model") {
                    currentList[msgIndex + 1]
                } else null

                val historyBefore = if (msgIndex > 0) currentList.take(msgIndex) else emptyList()
                val mode = AssistantMode.fromId(message.mode)
                val language = _uiState.value.selectedLanguage

                var finalContent = ""
                var finalSources: List<SourceLink> = emptyList()

                aiService.streamAssistantResponse(
                    prompt = trimmed,
                    history = historyBefore,
                    mode = mode,
                    language = language,
                    attachment = null
                ).collect { event ->
                    when (event) {
                        is StreamChunkEvent.Delta -> {
                            _uiState.update {
                                it.copy(
                                    streamingResponse = event.accumulatedText,
                                    streamingSources = event.sources
                                )
                            }
                        }
                        is StreamChunkEvent.Completed -> {
                            finalContent = event.finalText
                            finalSources = event.sources
                        }
                    }
                }

                if (nextModelMsg != null) {
                    chatRepository.updateMessageContent(
                        userId = currentUserId,
                        chatId = chatId,
                        messageId = nextModelMsg.id,
                        newContent = finalContent,
                        sources = finalSources.map { it.serialize() }
                    ).getOrThrow()
                } else {
                    chatRepository.addMessage(
                        userId = currentUserId,
                        chatId = chatId,
                        role = "model",
                        content = finalContent,
                        mode = mode.id,
                        sources = finalSources.map { it.serialize() }
                    ).getOrThrow()
                }

                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        streamingResponse = "",
                        streamingSources = emptyList()
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Edit & regenerate failed", e)
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        streamingResponse = "",
                        errorBanner = e.localizedMessage ?: "Failed to regenerate response."
                    )
                }
            }
        }
    }

    fun regenerateModelMessage(modelMessage: ChatMessage) {
        val chatId = _uiState.value.activeChatId ?: return
        if (_uiState.value.isGenerating) return

        val messages = activeMessages.value
        val modelIndex = messages.indexOfFirst { it.id == modelMessage.id }
        if (modelIndex <= 0) return

        val precedingUserMsg = messages.take(modelIndex).lastOrNull { it.role == "user" } ?: return
        val historyBeforeUser = messages.take(messages.indexOf(precedingUserMsg))
        val mode = AssistantMode.fromId(modelMessage.mode)
        val language = _uiState.value.selectedLanguage

        _uiState.update {
            it.copy(
                isGenerating = true,
                isGeneratingImage = (mode == AssistantMode.IMAGE),
                streamingResponse = "",
                streamingSources = emptyList(),
                errorBanner = null
            )
        }

        activeGenerationJob = viewModelScope.launch {
            try {
                if (mode == AssistantMode.IMAGE) {
                    val bmp = aiService.generateImage(precedingUserMsg.content)
                    _generatedBitmaps.update { it + (modelMessage.id to bmp) }
                    chatRepository.updateMessageContent(
                        userId = currentUserId,
                        chatId = chatId,
                        messageId = modelMessage.id,
                        newContent = "Regenerated image for: \"${precedingUserMsg.content}\""
                    ).getOrThrow()
                } else {
                    var finalContent = ""
                    var finalSources: List<SourceLink> = emptyList()
                    aiService.streamAssistantResponse(
                        prompt = precedingUserMsg.content,
                        history = historyBeforeUser,
                        mode = mode,
                        language = language,
                        attachment = null
                    ).collect { event ->
                        when (event) {
                            is StreamChunkEvent.Delta -> {
                                _uiState.update {
                                    it.copy(
                                        streamingResponse = event.accumulatedText,
                                        streamingSources = event.sources
                                    )
                                }
                            }
                            is StreamChunkEvent.Completed -> {
                                finalContent = event.finalText
                                finalSources = event.sources
                            }
                        }
                    }

                    chatRepository.updateMessageContent(
                        userId = currentUserId,
                        chatId = chatId,
                        messageId = modelMessage.id,
                        newContent = finalContent,
                        sources = finalSources.map { it.serialize() }
                    ).getOrThrow()
                }

                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        isGeneratingImage = false,
                        streamingResponse = "",
                        streamingSources = emptyList()
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Regenerate failed", e)
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        isGeneratingImage = false,
                        streamingResponse = "",
                        errorBanner = e.localizedMessage ?: "Failed to regenerate response."
                    )
                }
            }
        }
    }

    fun renameChat(chatId: String, newTitle: String) {
        viewModelScope.launch {
            chatRepository.renameChat(currentUserId, chatId, newTitle)
                .onFailure { err -> _uiState.update { it.copy(errorBanner = err.localizedMessage) } }
        }
    }

    fun deleteChat(chatId: String) {
        viewModelScope.launch {
            chatRepository.deleteChat(currentUserId, chatId)
                .onSuccess {
                    if (_uiState.value.activeChatId == chatId) {
                        startNewChat()
                    }
                }
                .onFailure { err -> _uiState.update { it.copy(errorBanner = err.localizedMessage) } }
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceAssistant.release()
    }

    private fun unwrapFirestoreError(error: Throwable, label: String): String {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val root = firestoreError ?: error
        Log.w(TAG, "Error observing $label", root)
        return root.message ?: "Failed to load $label"
    }

    companion object {
        fun provideFactory(
            currentUserId: String,
            userDisplayName: String,
            userEmail: String?,
            userPhotoUrl: String?,
            chatRepository: ChatRepository,
            prefsRepository: AppPreferencesRepository,
            voiceAssistant: ImraxVoiceAssistant
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ImraxViewModel(
                    currentUserId = currentUserId,
                    userDisplayName = userDisplayName,
                    userEmail = userEmail,
                    userPhotoUrl = userPhotoUrl,
                    chatRepository = chatRepository,
                    aiService = GeminiAiService(),
                    prefsRepository = prefsRepository,
                    voiceAssistant = voiceAssistant
                )
            }
        }
    }
}
