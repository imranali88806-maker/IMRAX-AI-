package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.auth.authStateFlow
import com.example.auth.signOutUser
import com.example.data.preferences.AppPreferencesRepository
import com.example.data.preferences.LocalAppSettings
import com.example.data.repository.ChatRepository
import com.example.firebase.configureAppCheck
import com.example.media.ImraxVoiceAssistant
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.MainChatShell
import com.example.ui.theme.ImraxAiTheme
import com.example.ui.viewmodel.ImraxViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure Firebase App Check with launch intent before rendering UI or creating ViewModels
        configureAppCheck(this, intent)

        enableEdgeToEdge()

        setContent {
            ImraxAppRoot()
        }
    }
}

@Composable
fun ImraxAppRoot(auth: FirebaseAuth = Firebase.auth) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val prefsRepository = remember(appContext) { AppPreferencesRepository(appContext) }
    val localSettings by prefsRepository.settingsFlow.collectAsStateWithLifecycle(
        initialValue = LocalAppSettings()
    )
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(
        initialValue = auth.currentUser
    )
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    ImraxAiTheme(darkTheme = localSettings.darkTheme) {
        val user = currentUser
        if (user == null) {
            AuthScreen(
                onAuthSuccess = {}
            )
        } else {
            key(user.uid) {
                val viewModel: ImraxViewModel = viewModel(
                    key = user.uid,
                    factory = remember(user.uid) {
                        val dbId = appContext.getString(R.string.firestore_database_id)
                        val db = FirebaseFirestore.getInstance(dbId)
                        ImraxViewModel.provideFactory(
                            currentUserId = user.uid,
                            userDisplayName = user.displayName ?: "IMRAX Explorer",
                            userEmail = user.email,
                            userPhotoUrl = user.photoUrl?.toString(),
                            chatRepository = ChatRepository(db),
                            prefsRepository = prefsRepository,
                            voiceAssistant = ImraxVoiceAssistant(appContext)
                        )
                    }
                )

                MainChatShell(
                    viewModel = viewModel,
                    userDisplayName = user.displayName ?: "IMRAX Explorer",
                    userEmail = user.email,
                    userPhotoUrl = user.photoUrl?.toString(),
                    onSignOut = {
                        signOutUser(
                            credentialManager = credentialManager,
                            scope = scope
                        )
                    }
                )
            }
        }
    }
}
