package com.example.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.data.model.AssistantLanguage
import com.example.data.model.AssistantMode
import com.example.data.model.ChatMessage
import com.example.data.model.SourceLink
import com.example.media.PendingAttachment
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

private const val TAG = "GeminiAiService"
private const val CHAT_MODEL_NAME = "gemini-3.8-flash"
private const val IMAGE_MODEL_NAME = "gemini-nano-banana-2.1"
private const val AI_TIMEOUT_MS = 60_000L

sealed interface StreamChunkEvent {
    data class Delta(val accumulatedText: String, val sources: List<SourceLink>) : StreamChunkEvent
    data class Completed(val finalText: String, val sources: List<SourceLink>) : StreamChunkEvent
}

class GeminiAiService {

    private val imageGenerationModel: GenerativeModel by lazy {
        val config = generationConfig {
            responseModalities = listOf(ResponseModality.IMAGE)
        }
        Firebase.ai.generativeModel(
            modelName = IMAGE_MODEL_NAME,
            generationConfig = config
        )
    }

    private fun buildModel(mode: AssistantMode, language: AssistantLanguage): GenerativeModel {
        val systemInstruction = buildSystemInstruction(mode, language)
        return if (mode == AssistantMode.SEARCH) {
            Firebase.ai.generativeModel(
                modelName = CHAT_MODEL_NAME,
                systemInstruction = systemInstruction,
                tools = listOf(Tool.googleSearch())
            )
        } else {
            Firebase.ai.generativeModel(
                modelName = CHAT_MODEL_NAME,
                systemInstruction = systemInstruction
            )
        }
    }

    private fun buildSystemInstruction(mode: AssistantMode, language: AssistantLanguage): Content {
        val langInstruction = when (language) {
            AssistantLanguage.ENGLISH ->
                "Respond in clear, natural English unless the user explicitly writes in Hindi or Assamese."
            AssistantLanguage.HINDI ->
                "Prefer responding in natural, fluent Hindi (हिन्दी, Devanagari script) unless the user asks for another language. Keep code snippets in standard syntax."
            AssistantLanguage.ASSAMESE ->
                "Prefer responding in natural, accurate Assamese (অসমীয়া, Assamese script) unless the user asks for another language. Keep code snippets in standard syntax."
        }

        val modeInstruction = when (mode) {
            AssistantMode.CHAT ->
                "You are IMRAX AI ('Your World. Your Intelligence.'), a helpful, concise, and deeply knowledgeable AI assistant."
            AssistantMode.CODING ->
                "You are IMRAX AI Code Studio. Provide clean, idiomatic, production-ready code with clear explanations and fenced markdown code blocks tagged with the language name."
            AssistantMode.SEARCH ->
                "You are IMRAX AI Web Search. Use Google Search grounding to verify facts and provide current, accurate answers with clear citations."
            AssistantMode.DOCUMENT ->
                "You are IMRAX AI Document & Vision Analyst. Carefully examine attached PDFs, documents, or images, extract key facts, and answer accurately."
            AssistantMode.IMAGE ->
                "You are IMRAX AI Image Studio assistant."
        }

        return content {
            text("$modeInstruction $langInstruction")
        }
    }

    fun streamAssistantResponse(
        prompt: String,
        history: List<ChatMessage>,
        mode: AssistantMode,
        language: AssistantLanguage,
        attachment: PendingAttachment?
    ): Flow<StreamChunkEvent> = flow {
        val model = buildModel(mode, language)
        val priorHistory = history
            .filter { it.content.isNotBlank() && it.attachmentType != "generated_image" }
            .takeLast(12)
            .map { msg ->
                content(role = if (msg.role == "user") "user" else "model") {
                    text(msg.content)
                }
            }

        val userContent = content(role = "user") {
            if (attachment != null) {
                if (attachment.isImage && attachment.bitmap != null) {
                    image(attachment.bitmap)
                } else if (attachment.bytes != null) {
                    inlineData(bytes = attachment.bytes, mimeType = attachment.mimeType)
                }
            }
            val effectivePrompt = prompt.ifBlank {
                if (attachment?.isImage == true) "Analyze this image in detail."
                else "Summarize and analyze the key points of this document."
            }
            text(effectivePrompt)
        }

        val accumulated = StringBuilder()
        val collectedSources = linkedMapOf<String, SourceLink>()

        try {
            withTimeout(AI_TIMEOUT_MS) {
                val stream = if (priorHistory.isNotEmpty() && attachment == null) {
                    val chat = model.startChat(history = priorHistory)
                    chat.sendMessageStream(userContent)
                } else {
                    val fullPrompt = priorHistory + userContent
                    model.generateContentStream(fullPrompt)
                }

                stream.collect { chunk ->
                    val chunkText = chunk.text.orEmpty()
                    if (chunkText.isNotEmpty()) {
                        accumulated.append(chunkText)
                    }
                    chunk.candidates.firstOrNull()?.groundingMetadata?.groundingChunks?.forEach { gChunk ->
                        val web = gChunk.web
                        val url = web?.uri
                        val title = web?.title ?: url
                        if (!url.isNullOrBlank()) {
                            collectedSources[url] = SourceLink(
                                title = title.orEmpty().ifBlank { url },
                                url = url
                            )
                        }
                    }
                    emit(
                        StreamChunkEvent.Delta(
                            accumulatedText = accumulated.toString(),
                            sources = collectedSources.values.take(10)
                        )
                    )
                }
            }

            val finalText = accumulated.toString().trim()
            if (finalText.isEmpty()) {
                throw IllegalStateException("IMRAX AI returned an empty response. Please try again.")
            }
            emit(
                StreamChunkEvent.Completed(
                    finalText = finalText,
                    sources = collectedSources.values.take(10)
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini streaming timed out", e)
            throw IllegalStateException("Request timed out after 60 seconds. Please try again.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini streaming failed", e)
            throw e
        }
    }

    suspend fun generateImage(
        prompt: String,
        inputBitmap: Bitmap? = null
    ): Bitmap {
        try {
            return withTimeout(AI_TIMEOUT_MS) {
                val response = if (inputBitmap != null) {
                    imageGenerationModel.generateContent(
                        content {
                            image(inputBitmap)
                            text(prompt.ifBlank { "Enhance and creatively restyle this image." })
                        }
                    )
                } else {
                    imageGenerationModel.generateContent(prompt)
                }
                val imagePart = response.candidates.firstOrNull()
                    ?.content?.parts?.filterIsInstance<ImagePart>()
                    ?.firstOrNull()
                imagePart?.image
                    ?: throw IllegalStateException("No image was returned by the model.")
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Image generation timed out", e)
            throw IllegalStateException("Image generation timed out after 60 seconds.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Image generation failed", e)
            throw e
        }
    }
}
