package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun ensureProfileAndCreateChat_authenticatedOwner_succeeds() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = ChatRepository(firestore)

        val profileResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.ensureUserProfile(uid, "Alice Rahman", ALICE_EMAIL, null)
        }
        assertTrue(profileResult.isSuccess)

        val chatResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createChatWithFirstMessage(
                userId = uid,
                title = "Assamese Poetry",
                mode = "chat",
                language = "as",
                userContent = "Write a poem about Brahmaputra"
            )
        }
        assertTrue(chatResult.isSuccess)
        val (chatId, _) = chatResult.getOrThrow()

        val chats = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeChats(uid).first { list -> list.any { it.id == chatId } }
        }
        assertEquals(1, chats.count { it.id == chatId })

        val addMsgResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.addMessage(
                userId = uid,
                chatId = chatId,
                role = "model",
                content = "Here is a poem about the mighty Brahmaputra.",
                mode = "chat",
                sources = listOf("Assam Culture|https://example.com")
            )
        }
        assertTrue(addMsgResult.isSuccess)

        val messages = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeMessages(uid, chatId).first { it.size >= 2 }
        }
        assertEquals(2, messages.size)
    }

    @Test
    fun observeChats_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = ChatRepository(firestore)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createChatWithFirstMessage(
                userId = aliceUid,
                title = "Private Notes",
                mode = "chat",
                language = "en",
                userContent = "Secret prompt"
            ).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = ChatRepository(firestore)

        var caught: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                bobRepo.observeChats(aliceUid).first()
            }
        } catch (e: Exception) {
            caught = e
        }
        val firestoreException = checkNotNull(caught?.unwrapFirestoreException()) {
            "Expected nested FirebaseFirestoreException, got $caught"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
    }

    @Test
    fun observeChats_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = ChatRepository(firestore)

        var caught: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeChats("alice_uid").first()
            }
        } catch (e: Exception) {
            caught = e
        }
        val firestoreException = checkNotNull(caught?.unwrapFirestoreException()) {
            "Expected nested FirebaseFirestoreException, got $caught"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
    }

    private companion object {
        const val ALICE_EMAIL = "alice_imrax@test.com"
        const val BOB_EMAIL = "bob_imrax@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
