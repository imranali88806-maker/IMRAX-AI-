package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.AssistantLanguage
import com.example.data.model.AssistantMode
import com.example.data.model.ChatMessage
import com.example.data.model.SourceLink
import com.example.media.MediaAttachmentLoader
import com.example.media.PendingAttachment
import com.example.ui.components.ImraxMarkdownContent
import com.example.ui.theme.BlueVibrant
import com.example.ui.theme.CyanElectric
import com.example.ui.theme.PurpleGlow
import com.example.ui.viewmodel.AppDestination
import com.example.ui.viewmodel.ImraxViewModel
import kotlinx.coroutines.launch

@Composable
fun MainChatShell(
    viewModel: ImraxViewModel,
    userDisplayName: String,
    userEmail: String?,
    userPhotoUrl: String?,
    onSignOut: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val chats by viewModel.chatThreads.collectAsStateWithLifecycle()
    val messages by viewModel.activeMessages.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val localSettings by viewModel.localSettings.collectAsStateWithLifecycle()
    val generatedBitmaps by viewModel.generatedBitmaps.collectAsStateWithLifecycle()
    val attachmentBitmaps by viewModel.attachmentPreviewBitmaps.collectAsStateWithLifecycle()
    val isListening by viewModel.voiceAssistant.isListening.collectAsStateWithLifecycle()
    val speakingMessageId by viewModel.voiceAssistant.speakingMessageId.collectAsStateWithLifecycle()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                MediaAttachmentLoader.loadImage(context, uri)
                    .onSuccess { viewModel.attachMedia(it) }
                    .onFailure { err -> snackbarHostState.showSnackbar(err.localizedMessage ?: "Failed to load image") }
            }
        }
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                MediaAttachmentLoader.loadDocument(context, uri)
                    .onSuccess { viewModel.attachMedia(it) }
                    .onFailure { err -> snackbarHostState.showSnackbar(err.localizedMessage ?: "Failed to read document") }
            }
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startVoiceListening()
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("Microphone permission is needed for voice input.")
            }
        }
    }

    val onMicClicked: () -> Unit = {
        if (isListening) {
            viewModel.stopVoiceListening()
        } else {
            val hasPerm = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (hasPerm) {
                viewModel.startVoiceListening()
            } else {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    val sidebar: @Composable () -> Unit = {
        ImraxSidebarContent(
            chats = chats,
            activeChatId = uiState.activeChatId,
            activeDestination = uiState.activeDestination,
            searchQuery = uiState.searchQuery,
            userDisplayName = userProfile?.displayName ?: userDisplayName,
            userEmail = userEmail,
            userPhotoUrl = userPhotoUrl,
            onSearchQueryChange = viewModel::updateSearchQuery,
            onNewChat = {
                viewModel.startNewChat()
                scope.launch { drawerState.close() }
            },
            onSelectChat = { thread ->
                viewModel.selectChat(thread)
                scope.launch { drawerState.close() }
            },
            onRenameChat = viewModel::renameChat,
            onDeleteChat = viewModel::deleteChat,
            onOpenExplore = {
                viewModel.navigateTo(AppDestination.EXPLORE)
                scope.launch { drawerState.close() }
            },
            onOpenSettings = {
                viewModel.navigateTo(AppDestination.SETTINGS)
                scope.launch { drawerState.close() }
            }
        )
    }

    if (uiState.editingMessage != null) {
        EditMessageDialog(
            initialText = uiState.editingMessage!!.content,
            onDismiss = viewModel::dismissEditingMessage,
            onConfirm = { newText ->
                viewModel.submitEditedUserMessage(uiState.editingMessage!!, newText)
            }
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpandedLayout = maxWidth >= 840.dp

        if (isExpandedLayout) {
            PermanentNavigationDrawer(
                drawerContent = {
                    PermanentDrawerSheet(
                        modifier = Modifier.width(300.dp),
                        drawerContainerColor = MaterialTheme.colorScheme.surface
                    ) {
                        sidebar()
                    }
                }
            ) {
                MainContentScaffold(
                    isExpandedLayout = true,
                    uiState = uiState,
                    messages = messages,
                    generatedBitmaps = generatedBitmaps,
                    attachmentBitmaps = attachmentBitmaps,
                    isListening = isListening,
                    speakingMessageId = speakingMessageId,
                    localSettings = localSettings,
                    userProfile = userProfile,
                    userDisplayName = userDisplayName,
                    userEmail = userEmail,
                    userPhotoUrl = userPhotoUrl,
                    snackbarHostState = snackbarHostState,
                    onOpenDrawer = {},
                    onNewChat = { viewModel.startNewChat() },
                    onSelectLanguage = viewModel::selectLanguage,
                    onSelectMode = viewModel::selectMode,
                    onInputChange = viewModel::updateInputText,
                    onPickPhoto = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onPickDocument = {
                        documentPickerLauncher.launch(
                            arrayOf("application/pdf", "text/*", "application/json")
                        )
                    },
                    onClearAttachment = viewModel::clearAttachment,
                    onMicClick = onMicClicked,
                    onSend = { viewModel.sendPrompt() },
                    onStopGeneration = viewModel::stopGeneration,
                    onSuggestionClick = { prompt, mode ->
                        viewModel.selectMode(mode)
                        viewModel.sendPrompt(prompt)
                    },
                    onEditUserMessage = viewModel::startEditingMessage,
                    onRegenerateMessage = viewModel::regenerateModelMessage,
                    onToggleSpeak = viewModel::toggleSpeakMessage,
                    onClearError = viewModel::clearError,
                    onBackToChat = { viewModel.navigateTo(AppDestination.CHAT) },
                    onLaunchExploreTool = { mode, starter ->
                        viewModel.startNewChat(mode)
                        viewModel.updateInputText(starter)
                    },
                    onToggleTheme = viewModel::toggleTheme,
                    onToggleAutoSpeak = viewModel::toggleAutoSpeak,
                    onSignOut = onSignOut
                )
            }
        } else {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(
                        modifier = Modifier.width(308.dp),
                        drawerContainerColor = MaterialTheme.colorScheme.surface
                    ) {
                        sidebar()
                    }
                }
            ) {
                MainContentScaffold(
                    isExpandedLayout = false,
                    uiState = uiState,
                    messages = messages,
                    generatedBitmaps = generatedBitmaps,
                    attachmentBitmaps = attachmentBitmaps,
                    isListening = isListening,
                    speakingMessageId = speakingMessageId,
                    localSettings = localSettings,
                    userProfile = userProfile,
                    userDisplayName = userDisplayName,
                    userEmail = userEmail,
                    userPhotoUrl = userPhotoUrl,
                    snackbarHostState = snackbarHostState,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onNewChat = { viewModel.startNewChat() },
                    onSelectLanguage = viewModel::selectLanguage,
                    onSelectMode = viewModel::selectMode,
                    onInputChange = viewModel::updateInputText,
                    onPickPhoto = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onPickDocument = {
                        documentPickerLauncher.launch(
                            arrayOf("application/pdf", "text/*", "application/json")
                        )
                    },
                    onClearAttachment = viewModel::clearAttachment,
                    onMicClick = onMicClicked,
                    onSend = { viewModel.sendPrompt() },
                    onStopGeneration = viewModel::stopGeneration,
                    onSuggestionClick = { prompt, mode ->
                        viewModel.selectMode(mode)
                        viewModel.sendPrompt(prompt)
                    },
                    onEditUserMessage = viewModel::startEditingMessage,
                    onRegenerateMessage = viewModel::regenerateModelMessage,
                    onToggleSpeak = viewModel::toggleSpeakMessage,
                    onClearError = viewModel::clearError,
                    onBackToChat = { viewModel.navigateTo(AppDestination.CHAT) },
                    onLaunchExploreTool = { mode, starter ->
                        viewModel.startNewChat(mode)
                        viewModel.updateInputText(starter)
                    },
                    onToggleTheme = viewModel::toggleTheme,
                    onToggleAutoSpeak = viewModel::toggleAutoSpeak,
                    onSignOut = onSignOut
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainContentScaffold(
    isExpandedLayout: Boolean,
    uiState: com.example.ui.viewmodel.ImraxUiState,
    messages: List<ChatMessage>,
    generatedBitmaps: Map<String, Bitmap>,
    attachmentBitmaps: Map<String, Bitmap>,
    isListening: Boolean,
    speakingMessageId: String?,
    localSettings: com.example.data.preferences.LocalAppSettings,
    userProfile: com.example.data.model.UserProfile?,
    userDisplayName: String,
    userEmail: String?,
    userPhotoUrl: String?,
    snackbarHostState: SnackbarHostState,
    onOpenDrawer: () -> Unit,
    onNewChat: () -> Unit,
    onSelectLanguage: (AssistantLanguage) -> Unit,
    onSelectMode: (AssistantMode) -> Unit,
    onInputChange: (String) -> Unit,
    onPickPhoto: () -> Unit,
    onPickDocument: () -> Unit,
    onClearAttachment: () -> Unit,
    onMicClick: () -> Unit,
    onSend: () -> Unit,
    onStopGeneration: () -> Unit,
    onSuggestionClick: (String, AssistantMode) -> Unit,
    onEditUserMessage: (ChatMessage) -> Unit,
    onRegenerateMessage: (ChatMessage) -> Unit,
    onToggleSpeak: (ChatMessage) -> Unit,
    onClearError: () -> Unit,
    onBackToChat: () -> Unit,
    onLaunchExploreTool: (AssistantMode, String) -> Unit,
    onToggleTheme: (Boolean) -> Unit,
    onToggleAutoSpeak: (Boolean) -> Unit,
    onSignOut: () -> Unit
) {
    var langMenuOpen by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.96f)
                ),
                navigationIcon = {
                    if (!isExpandedLayout) {
                        IconButton(
                            onClick = onOpenDrawer,
                            modifier = Modifier.testTag("sidebar_toggle_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Menu,
                                contentDescription = stringResource(R.string.cd_open_sidebar)
                            )
                        }
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.imrax_logo_icon_1794673946575),
                            contentDescription = stringResource(R.string.cd_imrax_logo),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    Box {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier
                                .clip(CircleShape)
                                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                .clickable { langMenuOpen = true }
                                .testTag("language_selector_button")
                        ) {
                            Text(
                                text = when (uiState.selectedLanguage) {
                                    AssistantLanguage.ENGLISH -> "EN"
                                    AssistantLanguage.HINDI -> "हिं"
                                    AssistantLanguage.ASSAMESE -> "অস"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = langMenuOpen,
                            onDismissRequest = { langMenuOpen = false }
                        ) {
                            AssistantLanguage.entries.forEach { lang ->
                                DropdownMenuItem(
                                    text = { Text("${lang.nativeLabel} · ${lang.displayName}") },
                                    trailingIcon = {
                                        if (uiState.selectedLanguage == lang) {
                                            Icon(Icons.Rounded.Check, contentDescription = null)
                                        }
                                    },
                                    onClick = {
                                        langMenuOpen = false
                                        onSelectLanguage(lang)
                                    }
                                )
                            }
                        }
                    }
                    IconButton(onClick = onNewChat) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = stringResource(R.string.new_chat)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
                .imePadding()
        ) {
            when (uiState.activeDestination) {
                AppDestination.EXPLORE -> {
                    ExploreAiScreen(
                        onBackToChat = onBackToChat,
                        onLaunchExploreTool = onLaunchExploreTool
                    )
                }
                AppDestination.SETTINGS -> {
                    SettingsAndProfileScreen(
                        userProfile = userProfile,
                        userDisplayName = userDisplayName,
                        userEmail = userEmail,
                        userPhotoUrl = userPhotoUrl,
                        localSettings = localSettings,
                        onBackToChat = onBackToChat,
                        onToggleTheme = onToggleTheme,
                        onSelectLanguage = onSelectLanguage,
                        onToggleAutoSpeak = onToggleAutoSpeak,
                        onSignOut = onSignOut
                    )
                }
                AppDestination.CHAT -> {
                    val isWelcomeState = messages.isEmpty() && !uiState.isGenerating && uiState.activeChatId == null
                    if (isWelcomeState) {
                        WelcomeLandingState(
                            uiState = uiState,
                            isListening = isListening,
                            onSelectMode = onSelectMode,
                            onInputChange = onInputChange,
                            onPickPhoto = onPickPhoto,
                            onPickDocument = onPickDocument,
                            onClearAttachment = onClearAttachment,
                            onMicClick = onMicClick,
                            onSend = onSend,
                            onStopGeneration = onStopGeneration,
                            onSuggestionClick = onSuggestionClick,
                            onClearError = onClearError
                        )
                    } else {
                        ActiveConversationView(
                            uiState = uiState,
                            messages = messages,
                            generatedBitmaps = generatedBitmaps,
                            attachmentBitmaps = attachmentBitmaps,
                            isListening = isListening,
                            speakingMessageId = speakingMessageId,
                            onSelectMode = onSelectMode,
                            onInputChange = onInputChange,
                            onPickPhoto = onPickPhoto,
                            onPickDocument = onPickDocument,
                            onClearAttachment = onClearAttachment,
                            onMicClick = onMicClick,
                            onSend = onSend,
                            onStopGeneration = onStopGeneration,
                            onEditUserMessage = onEditUserMessage,
                            onRegenerateMessage = onRegenerateMessage,
                            onToggleSpeak = onToggleSpeak,
                            onClearError = onClearError
                        )
                    }
                }
            }
        }
    }
}

private data class PromptSuggestion(
    val title: String,
    val prompt: String,
    val mode: AssistantMode
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WelcomeLandingState(
    uiState: com.example.ui.viewmodel.ImraxUiState,
    isListening: Boolean,
    onSelectMode: (AssistantMode) -> Unit,
    onInputChange: (String) -> Unit,
    onPickPhoto: () -> Unit,
    onPickDocument: () -> Unit,
    onClearAttachment: () -> Unit,
    onMicClick: () -> Unit,
    onSend: () -> Unit,
    onStopGeneration: () -> Unit,
    onSuggestionClick: (String, AssistantMode) -> Unit,
    onClearError: () -> Unit
) {
    val suggestions = remember(uiState.selectedLanguage) {
        when (uiState.selectedLanguage) {
            AssistantLanguage.ASSAMESE -> listOf(
                PromptSuggestion("অসমীয়া কবিতা", "ব্ৰহ্মপুত্ৰ নদৰ বিষয়ে এটি সুন্দৰ অসমীয়া কবিতা লিখা।", AssistantMode.CHAT),
                PromptSuggestion("Web Search", "অসমৰ শেহতীয়া গুৰুত্বপূৰ্ণ বাতৰি আৰু তথ্য বিচাৰি উলিওৱা।", AssistantMode.SEARCH),
                PromptSuggestion("Code Studio", "Write a clean Jetpack Compose animated search bar in Kotlin.", AssistantMode.CODING),
                PromptSuggestion("Image Studio", "Futuristic neon Assam tea garden at sunrise, cyber-organic art", AssistantMode.IMAGE)
            )
            AssistantLanguage.HINDI -> listOf(
                PromptSuggestion("हिन्दी सारांश", "भारत में AI और क्वांटम तकनीक के भविष्य पर संक्षिप्त जानकारी दें।", AssistantMode.CHAT),
                PromptSuggestion("Live Search", "2026 की प्रमुख अंतरिक्ष और विज्ञान उपलब्धियां खोजें।", AssistantMode.SEARCH),
                PromptSuggestion("Code Studio", "Python में FastAPI REST endpoint और error handling लिखें।", AssistantMode.CODING),
                PromptSuggestion("Image Studio", "Minimalist cyberpunk skyline with cyan and violet lights", AssistantMode.IMAGE)
            )
            AssistantLanguage.ENGLISH -> listOf(
                PromptSuggestion("Live Web Search", "What are the top AI and science breakthroughs this week?", AssistantMode.SEARCH),
                PromptSuggestion("Code Studio", "Write a clean Kotlin Flow debounce & retry utility with tests.", AssistantMode.CODING),
                PromptSuggestion("Generate Art", "Futuristic dark navy and cyan holographic AI core, 3D render", AssistantMode.IMAGE),
                PromptSuggestion("Multilingual", "Explain quantum computing simply in English, Hindi, and Assamese.", AssistantMode.CHAT)
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(78.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .border(
                        width = 1.5.dp,
                        brush = Brush.linearGradient(listOf(CyanElectric, BlueVibrant, PurpleGlow)),
                        shape = RoundedCornerShape(22.dp)
                    )
            ) {
                Image(
                    painter = painterResource(id = R.drawable.imrax_logo_icon_1794673946575),
                    contentDescription = stringResource(R.string.cd_imrax_logo),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = stringResource(R.string.welcome_heading),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = stringResource(R.string.tagline),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            ModeSelectorBar(
                selectedMode = uiState.selectedMode,
                onSelectMode = onSelectMode
            )

            Spacer(modifier = Modifier.height(14.dp))

            ErrorBannerIfPresent(uiState.errorBanner, onClearError)

            // Large Chat Input Box (Centered on Welcome Screen)
            ImraxComposerBox(
                inputText = uiState.inputText,
                selectedMode = uiState.selectedMode,
                pendingAttachment = uiState.pendingAttachment,
                isGenerating = uiState.isGenerating,
                isListening = isListening,
                largeWelcomeStyle = true,
                onInputChange = onInputChange,
                onPickPhoto = onPickPhoto,
                onPickDocument = onPickDocument,
                onClearAttachment = onClearAttachment,
                onMicClick = onMicClick,
                onSend = onSend,
                onStopGeneration = onStopGeneration
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Suggested Prompts BELOW the chat box
            Text(
                text = "Suggested Prompts",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                suggestions.forEach { item ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier
                            .widthIn(min = 150.dp, max = 330.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                            .clickable { onSuggestionClick(item.prompt, item.mode) }
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = item.prompt,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveConversationView(
    uiState: com.example.ui.viewmodel.ImraxUiState,
    messages: List<ChatMessage>,
    generatedBitmaps: Map<String, Bitmap>,
    attachmentBitmaps: Map<String, Bitmap>,
    isListening: Boolean,
    speakingMessageId: String?,
    onSelectMode: (AssistantMode) -> Unit,
    onInputChange: (String) -> Unit,
    onPickPhoto: () -> Unit,
    onPickDocument: () -> Unit,
    onClearAttachment: () -> Unit,
    onMicClick: () -> Unit,
    onSend: () -> Unit,
    onStopGeneration: () -> Unit,
    onEditUserMessage: (ChatMessage) -> Unit,
    onRegenerateMessage: (ChatMessage) -> Unit,
    onToggleSpeak: (ChatMessage) -> Unit,
    onClearError: () -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, uiState.streamingResponse.length, uiState.isGenerating) {
        val totalItems = messages.size + if (uiState.isGenerating) 1 else 0
        if (totalItems > 0) {
            listState.animateScrollToItem(totalItems - 1)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .widthIn(max = 760.dp)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                ChatMessageBubble(
                    message = msg,
                    generatedBitmap = generatedBitmaps[msg.id],
                    attachmentBitmap = attachmentBitmaps[msg.id],
                    isSpeaking = speakingMessageId == msg.id,
                    onEdit = { onEditUserMessage(msg) },
                    onRegenerate = { onRegenerateMessage(msg) },
                    onToggleSpeak = { onToggleSpeak(msg) }
                )
            }

            if (uiState.isGenerating) {
                item(key = "streaming_indicator") {
                    StreamingAssistantBubble(
                        streamingText = uiState.streamingResponse,
                        sources = uiState.streamingSources,
                        isGeneratingImage = uiState.isGeneratingImage
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            ErrorBannerIfPresent(uiState.errorBanner, onClearError)
            ModeSelectorBar(
                selectedMode = uiState.selectedMode,
                onSelectMode = onSelectMode
            )
            Spacer(modifier = Modifier.height(8.dp))
            ImraxComposerBox(
                inputText = uiState.inputText,
                selectedMode = uiState.selectedMode,
                pendingAttachment = uiState.pendingAttachment,
                isGenerating = uiState.isGenerating,
                isListening = isListening,
                largeWelcomeStyle = false,
                onInputChange = onInputChange,
                onPickPhoto = onPickPhoto,
                onPickDocument = onPickDocument,
                onClearAttachment = onClearAttachment,
                onMicClick = onMicClick,
                onSend = onSend,
                onStopGeneration = onStopGeneration
            )
        }
    }
}

@Composable
private fun ModeSelectorBar(
    selectedMode: AssistantMode,
    onSelectMode: (AssistantMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AssistantMode.entries.forEach { mode ->
            val selected = mode == selectedMode
            FilterChip(
                selected = selected,
                onClick = { onSelectMode(mode) },
                leadingIcon = {
                    Icon(
                        imageVector = when (mode) {
                            AssistantMode.CHAT -> Icons.Rounded.AutoAwesome
                            AssistantMode.CODING -> Icons.Rounded.Code
                            AssistantMode.SEARCH -> Icons.Rounded.Public
                            AssistantMode.IMAGE -> Icons.Rounded.AddPhotoAlternate
                            AssistantMode.DOCUMENT -> Icons.Rounded.Description
                        },
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                label = {
                    Text(
                        text = mode.label,
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.primary,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

@Composable
private fun ImraxComposerBox(
    inputText: String,
    selectedMode: AssistantMode,
    pendingAttachment: PendingAttachment?,
    isGenerating: Boolean,
    isListening: Boolean,
    largeWelcomeStyle: Boolean,
    onInputChange: (String) -> Unit,
    onPickPhoto: () -> Unit,
    onPickDocument: () -> Unit,
    onClearAttachment: () -> Unit,
    onMicClick: () -> Unit,
    onSend: () -> Unit,
    onStopGeneration: () -> Unit
) {
    var attachMenuOpen by remember { mutableStateOf(false) }
    val canSend = (inputText.isNotBlank() || pendingAttachment != null) && !isGenerating

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.2.dp,
                brush = Brush.horizontalGradient(
                    listOf(
                        MaterialTheme.colorScheme.outline,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        PurpleGlow.copy(alpha = 0.45f)
                    )
                ),
                shape = RoundedCornerShape(24.dp)
            )
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            if (pendingAttachment != null) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (pendingAttachment.bitmap != null) {
                            Image(
                                bitmap = pendingAttachment.bitmap.asImageBitmap(),
                                contentDescription = pendingAttachment.displayName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = pendingAttachment.displayName,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 200.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = onClearAttachment,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Remove attachment",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = inputText,
                onValueChange = onInputChange,
                placeholder = {
                    Text(
                        text = when (selectedMode) {
                            AssistantMode.IMAGE -> "Describe an image to generate…"
                            AssistantMode.SEARCH -> "Ask anything to search the web…"
                            AssistantMode.CODING -> "Ask a coding or debugging question…"
                            AssistantMode.DOCUMENT -> "Attach a PDF/image and ask anything…"
                            AssistantMode.CHAT -> stringResource(R.string.input_placeholder)
                        },
                        style = MaterialTheme.typography.bodyLarge
                    )
                },
                minLines = if (largeWelcomeStyle) 2 else 1,
                maxLines = 5,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("chat_input_field")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        IconButton(
                            onClick = { attachMenuOpen = true },
                            modifier = Modifier.testTag("attach_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AttachFile,
                                contentDescription = stringResource(R.string.cd_attach_file),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        DropdownMenu(
                            expanded = attachMenuOpen,
                            onDismissRequest = { attachMenuOpen = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Upload Photo (Vision)") },
                                leadingIcon = {
                                    Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null)
                                },
                                onClick = {
                                    attachMenuOpen = false
                                    onPickPhoto()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Upload PDF / Document") },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Description, contentDescription = null)
                                },
                                onClick = {
                                    attachMenuOpen = false
                                    onPickDocument()
                                }
                            )
                        }
                    }

                    IconButton(
                        onClick = onMicClick,
                        modifier = Modifier.testTag("voice_input_button")
                    ) {
                        Icon(
                            imageVector = if (isListening) Icons.Rounded.GraphicEq else Icons.Rounded.Mic,
                            contentDescription = stringResource(R.string.cd_voice_input),
                            tint = if (isListening) CyanElectric else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (isListening) {
                        Text(
                            text = "Listening…",
                            style = MaterialTheme.typography.labelMedium,
                            color = CyanElectric
                        )
                    }
                }

                if (isGenerating) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .clickable(onClick = onStopGeneration)
                            .testTag("stop_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Stop,
                            contentDescription = stringResource(R.string.cd_stop_generation),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                } else {
                    val bgBrush = if (canSend) {
                        Brush.linearGradient(listOf(CyanElectric, BlueVibrant, PurpleGlow))
                    } else {
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.surfaceContainerHigh,
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(bgBrush)
                            .clickable(enabled = canSend, onClick = onSend)
                            .testTag("send_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = stringResource(R.string.cd_send_message),
                            tint = if (canSend) Color(0xFF060B19) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatMessageBubble(
    message: ChatMessage,
    generatedBitmap: Bitmap?,
    attachmentBitmap: Bitmap?,
    isSpeaking: Boolean,
    onEdit: () -> Unit,
    onRegenerate: () -> Unit,
    onToggleSpeak: () -> Unit
) {
    val isUser = message.role == "user"
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            kotlinx.coroutines.delay(1800)
            copied = false
        }
    }

    if (isUser) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.widthIn(max = 520.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.border(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                        RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp)
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        if (attachmentBitmap != null) {
                            Image(
                                bitmap = attachmentBitmap.asImageBitmap(),
                                contentDescription = message.attachmentName ?: "Uploaded image",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .padding(bottom = 8.dp)
                            )
                        } else if (message.attachmentType != "none" && !message.attachmentName.isNullOrBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Description,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = message.attachmentName,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Text(
                            text = message.content,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                Row(modifier = Modifier.padding(top = 2.dp)) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = "Edit prompt",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    } else {
        val parsedSources = remember(message.sources) {
            message.sources.map { SourceLink.parse(it) }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Image(
                painter = painterResource(id = R.drawable.imrax_logo_icon_1794673946575),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(30.dp)
                    .clip(RoundedCornerShape(9.dp))
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Surface(
                    shape = RoundedCornerShape(6.dp, 20.dp, 20.dp, 20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(6.dp, 20.dp, 20.dp, 20.dp)
                        )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        if (generatedBitmap != null) {
                            Image(
                                bitmap = generatedBitmap.asImageBitmap(),
                                contentDescription = "AI generated artwork",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .padding(bottom = 10.dp)
                            )
                        }
                        ImraxMarkdownContent(
                            content = message.content,
                            sources = parsedSources
                        )
                    }
                }
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(message.content))
                            copied = true
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                            contentDescription = "Copy response",
                            tint = if (copied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = onToggleSpeak,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = if (isSpeaking) Icons.Rounded.Stop else Icons.Rounded.VolumeUp,
                            contentDescription = if (isSpeaking) "Stop speaking" else "Read aloud",
                            tint = if (isSpeaking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = onRegenerate,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Regenerate response",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamingAssistantBubble(
    streamingText: String,
    sources: List<SourceLink>,
    isGeneratingImage: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Image(
            painter = painterResource(id = R.drawable.imrax_logo_icon_1794673946575),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(30.dp)
                .clip(RoundedCornerShape(9.dp))
        )
        Spacer(modifier = Modifier.width(10.dp))
        Surface(
            shape = RoundedCornerShape(6.dp, 20.dp, 20.dp, 20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier
                .weight(1f)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                    RoundedCornerShape(6.dp, 20.dp, 20.dp, 20.dp)
                )
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                if (isGeneratingImage) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Synthesizing image with Nano Banana 2.1 (may take 30–60s)…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else if (streamingText.isBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "IMRAX AI is thinking…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    ImraxMarkdownContent(
                        content = streamingText,
                        sources = sources
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorBannerIfPresent(error: String?, onDismiss: () -> Unit) {
    AnimatedVisibility(visible = !error.isNullOrBlank()) {
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = error.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Dismiss error",
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EditMessageDialog(
    initialText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialText) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit & Regenerate") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                minLines = 2,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank()
            ) { Text("Regenerate") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
