package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class AssistantLanguage(
    val code: String,
    val displayName: String,
    val nativeLabel: String,
    val speechLocaleTag: String
) {
    ENGLISH("en", "English", "English", "en-US"),
    HINDI("hi", "Hindi", "हिन्दी", "hi-IN"),
    ASSAMESE("as", "Assamese", "অসমীয়া", "as-IN");

    companion object {
        fun fromCode(code: String?): AssistantLanguage =
            entries.firstOrNull { it.code == code } ?: ENGLISH
    }
}

enum class AssistantMode(
    val id: String,
    val label: String,
    val description: String
) {
    CHAT("chat", "General AI", "Smart conversations in English, Hindi & Assamese"),
    CODING("coding", "Code Studio", "Clean, production-ready code, debugging & architecture"),
    SEARCH("search", "Web Search", "Live grounded answers with verifiable source links"),
    IMAGE("image", "Image Studio", "Generate high-craft AI artwork from text prompts"),
    DOCUMENT("document", "Doc & PDF", "Analyze PDFs, notes, contracts & uploaded images");

    companion object {
        fun fromId(id: String?): AssistantMode =
            entries.firstOrNull { it.id == id } ?: CHAT
    }
}

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "IMRAX Explorer",
    val email: String? = null,
    val photoUrl: String? = null,
    val preferredLanguage: String = AssistantLanguage.ENGLISH.code,
    val darkTheme: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ChatThread(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "New Chat",
    val mode: String = AssistantMode.CHAT.id,
    val language: String = AssistantLanguage.ENGLISH.code,
    val lastMessagePreview: String = "",
    val messageCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val chatId: String = "",
    val role: String = "user",
    val content: String = "",
    val mode: String = AssistantMode.CHAT.id,
    val attachmentType: String = "none",
    val attachmentName: String? = null,
    val sources: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class SourceLink(
    val title: String,
    val url: String
) {
    fun serialize(): String = "${title.replace("|", " ")}|$url"

    companion object {
        fun parse(raw: String): SourceLink {
            val parts = raw.split("|", limit = 2)
            return if (parts.size == 2) {
                SourceLink(title = parts[0].ifBlank { parts[1] }, url = parts[1])
            } else {
                SourceLink(title = raw, url = raw)
            }
        }
    }
}
