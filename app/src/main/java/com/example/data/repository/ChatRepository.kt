package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.AssistantLanguage
import com.example.data.model.ChatMessage
import com.example.data.model.ChatThread
import com.example.data.model.UserProfile
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class ChatRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    fun observeUserProfile(userId: String): Flow<UserProfile?> {
        val docRef = db.collection("users").document(userId)
        return docRef.snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(
                        UserProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                } else {
                    null
                }
            }
            .catch { error ->
                unwrapAndLog(error, OperationType.GET, docRef.path)
                throw error
            }
    }

    suspend fun ensureUserProfile(
        userId: String,
        displayName: String,
        email: String?,
        photoUrl: String?
    ): Result<Unit> = runCatching {
        val docRef = db.collection("users").document(userId)
        try {
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val safeName = displayName.trim().ifBlank { "IMRAX User" }.take(100)
                val payload = buildMap<String, Any> {
                    put("userId", userId)
                    put("displayName", safeName)
                    email?.takeIf { it.isNotBlank() }?.let { put("email", it.take(200)) }
                    photoUrl?.takeIf { it.isNotBlank() }?.let { put("photoUrl", it.take(500)) }
                    put("preferredLanguage", AssistantLanguage.ENGLISH.code)
                    put("darkTheme", true)
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.set(payload).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun updateUserPreferences(
        userId: String,
        preferredLanguage: String? = null,
        darkTheme: Boolean? = null,
        displayName: String? = null
    ): Result<Unit> = runCatching {
        val docRef = db.collection("users").document(userId)
        val updates = buildMap<String, Any> {
            preferredLanguage?.let { put("preferredLanguage", it) }
            darkTheme?.let { put("darkTheme", it) }
            displayName?.trim()?.takeIf { it.isNotBlank() }?.let { put("displayName", it.take(100)) }
            put("updatedAt", FieldValue.serverTimestamp())
        }
        try {
            docRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    fun observeChats(userId: String): Flow<List<ChatThread>> {
        val collectionRef = db.collection("users").document(userId).collection("chats")
        return collectionRef.snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    ChatThread::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                unwrapAndLog(error, OperationType.LIST, collectionRef.path)
                throw error
            }
    }

    fun observeMessages(userId: String, chatId: String): Flow<List<ChatMessage>> {
        val collectionRef = db.collection("users")
            .document(userId)
            .collection("chats")
            .document(chatId)
            .collection("messages")
        return collectionRef.snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    ChatMessage::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedBy { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                unwrapAndLog(error, OperationType.LIST, collectionRef.path)
                throw error
            }
    }

    suspend fun createChatWithFirstMessage(
        userId: String,
        title: String,
        mode: String,
        language: String,
        userContent: String,
        attachmentType: String = "none",
        attachmentName: String? = null
    ): Result<Pair<String, String>> = runCatching {
        val chatsRef = db.collection("users").document(userId).collection("chats")
        val chatDocRef = chatsRef.document()
        val msgDocRef = chatDocRef.collection("messages").document()
        val safeTitle = title.trim().ifBlank { "New Chat" }.take(160)
        val preview = userContent.trim().ifBlank { attachmentName ?: "Attachment" }.take(300)

        val chatPayload = mapOf(
            "userId" to userId,
            "title" to safeTitle,
            "mode" to mode,
            "language" to language,
            "lastMessagePreview" to preview,
            "messageCount" to 1L,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        val messagePayload = buildMap<String, Any> {
            put("userId", userId)
            put("chatId", chatDocRef.id)
            put("role", "user")
            put("content", userContent.take(100000))
            put("mode", mode)
            put("attachmentType", attachmentType)
            attachmentName?.takeIf { it.isNotBlank() }?.let { put("attachmentName", it.take(200)) }
            put("sources", emptyList<String>())
            put("createdAt", FieldValue.serverTimestamp())
            put("updatedAt", FieldValue.serverTimestamp())
        }

        try {
            val batch = db.batch()
            batch.set(chatDocRef, chatPayload)
            batch.set(msgDocRef, messagePayload)
            batch.commit().await()
            chatDocRef.id to msgDocRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, chatDocRef.path)
            throw e
        }
    }

    suspend fun addMessage(
        userId: String,
        chatId: String,
        role: String,
        content: String,
        mode: String,
        attachmentType: String = "none",
        attachmentName: String? = null,
        sources: List<String> = emptyList()
    ): Result<String> = runCatching {
        val chatDocRef = db.collection("users").document(userId)
            .collection("chats").document(chatId)
        val msgDocRef = chatDocRef.collection("messages").document()
        val preview = content.trim().ifBlank { attachmentName ?: "Response" }.take(300)

        val messagePayload = buildMap<String, Any> {
            put("userId", userId)
            put("chatId", chatId)
            put("role", role)
            put("content", content.take(100000))
            put("mode", mode)
            put("attachmentType", attachmentType)
            attachmentName?.takeIf { it.isNotBlank() }?.let { put("attachmentName", it.take(200)) }
            put("sources", sources.take(10))
            put("createdAt", FieldValue.serverTimestamp())
            put("updatedAt", FieldValue.serverTimestamp())
        }

        val chatUpdates = mapOf(
            "lastMessagePreview" to preview,
            "mode" to mode,
            "messageCount" to FieldValue.increment(1L),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        try {
            val batch = db.batch()
            batch.set(msgDocRef, messagePayload)
            batch.update(chatDocRef, chatUpdates)
            batch.commit().await()
            msgDocRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, msgDocRef.path)
            throw e
        }
    }

    suspend fun updateMessageContent(
        userId: String,
        chatId: String,
        messageId: String,
        newContent: String,
        sources: List<String> = emptyList()
    ): Result<Unit> = runCatching {
        val msgDocRef = db.collection("users").document(userId)
            .collection("chats").document(chatId)
            .collection("messages").document(messageId)
        val updates = mapOf(
            "content" to newContent.take(100000),
            "sources" to sources.take(10),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            msgDocRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, msgDocRef.path)
            throw e
        }
    }

    suspend fun deleteMessage(
        userId: String,
        chatId: String,
        messageId: String
    ): Result<Unit> = runCatching {
        val msgDocRef = db.collection("users").document(userId)
            .collection("chats").document(chatId)
            .collection("messages").document(messageId)
        try {
            msgDocRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, msgDocRef.path)
            throw e
        }
    }

    suspend fun renameChat(
        userId: String,
        chatId: String,
        newTitle: String
    ): Result<Unit> = runCatching {
        val chatDocRef = db.collection("users").document(userId)
            .collection("chats").document(chatId)
        val safeTitle = newTitle.trim().ifBlank { "Untitled Chat" }.take(160)
        try {
            chatDocRef.update(
                mapOf(
                    "title" to safeTitle,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, chatDocRef.path)
            throw e
        }
    }

    suspend fun deleteChat(userId: String, chatId: String): Result<Unit> = runCatching {
        val chatDocRef = db.collection("users").document(userId)
            .collection("chats").document(chatId)
        try {
            val messages = chatDocRef.collection("messages").get().await()
            val batch = db.batch()
            messages.documents.forEach { batch.delete(it.reference) }
            batch.delete(chatDocRef)
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, chatDocRef.path)
            throw e
        }
    }

    private fun unwrapAndLog(error: Throwable, operationType: OperationType, path: String) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) return
        val exceptionToLog = firestoreError ?: (error as? Exception) ?: return
        handleFirestoreError(exceptionToLog, operationType, path)
    }
}
