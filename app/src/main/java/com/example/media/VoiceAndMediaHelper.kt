package com.example.media

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.data.model.AssistantLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.UUID

private const val TAG = "VoiceAndMedia"
private const val MAX_IMAGE_DIM = 1536
private const val MAX_INLINE_DOC_BYTES = 14 * 1024 * 1024 // 14 MB safe under 20MB limit

data class PendingAttachment(
    val id: String = UUID.randomUUID().toString(),
    val displayName: String,
    val mimeType: String,
    val isImage: Boolean,
    val bitmap: Bitmap? = null,
    val bytes: ByteArray? = null
)

object MediaAttachmentLoader {

    suspend fun loadImage(context: Context, uri: Uri): Result<PendingAttachment> =
        withContext(Dispatchers.IO) {
            runCatching {
                val resolver = context.contentResolver
                val name = queryDisplayName(context, uri) ?: "Image.jpg"
                val mimeType = resolver.getType(uri) ?: "image/jpeg"

                val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, boundsOpts)
                }

                var sampleSize = 1
                val maxDim = maxOf(boundsOpts.outWidth, boundsOpts.outHeight)
                while (maxDim / sampleSize > MAX_IMAGE_DIM) {
                    sampleSize *= 2
                }

                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                val decoded = resolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, decodeOpts)
                } ?: throw IllegalStateException("Unable to decode selected image.")

                val scaled = scaleBitmapIfNeeded(decoded, MAX_IMAGE_DIM)
                val bytes = ByteArrayOutputStream().use { out ->
                    scaled.compress(Bitmap.CompressFormat.JPEG, 88, out)
                    out.toByteArray()
                }
                PendingAttachment(
                    displayName = name,
                    mimeType = mimeType,
                    isImage = true,
                    bitmap = scaled,
                    bytes = bytes
                )
            }
        }

    suspend fun loadDocument(context: Context, uri: Uri): Result<PendingAttachment> =
        withContext(Dispatchers.IO) {
            runCatching {
                val resolver = context.contentResolver
                val name = queryDisplayName(context, uri) ?: "Document.pdf"
                val mimeType = resolver.getType(uri) ?: "application/pdf"
                val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalStateException("Unable to read selected document.")

                if (bytes.isEmpty()) {
                    throw IllegalStateException("Selected document is empty.")
                }
                if (bytes.size > MAX_INLINE_DOC_BYTES) {
                    throw IllegalStateException("Document exceeds 14 MB limit. Please select a smaller file.")
                }

                PendingAttachment(
                    displayName = name,
                    mimeType = mimeType,
                    isImage = mimeType.startsWith("image/"),
                    bitmap = if (mimeType.startsWith("image/")) {
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    } else {
                        null
                    },
                    bytes = bytes
                )
            }
        }

    private fun scaleBitmapIfNeeded(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDimension && height <= maxDimension) return bitmap
        val ratio = width.toFloat() / height.toFloat()
        val targetW: Int
        val targetH: Int
        if (width > height) {
            targetW = maxDimension
            targetH = (maxDimension / ratio).toInt().coerceAtLeast(1)
        } else {
            targetH = maxDimension
            targetW = (maxDimension * ratio).toInt().coerceAtLeast(1)
        }
        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) cursor.getString(idx) else null
                    } else null
                }
        } catch (_: Exception) {
            null
        }
    }
}

class ImraxVoiceAssistant(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var speechRecognizer: SpeechRecognizer? = null

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _speakingMessageId = MutableStateFlow<String?>(null)
    val speakingMessageId: StateFlow<String?> = _speakingMessageId.asStateFlow()

    init {
        try {
            tts = TextToSpeech(context.applicationContext) { status ->
                ttsReady = status == TextToSpeech.SUCCESS
            }
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    _speakingMessageId.value = null
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _speakingMessageId.value = null
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "TTS initialization warning: ${e.message}")
        }
    }

    fun speak(messageId: String, text: String, language: AssistantLanguage) {
        if (!ttsReady) return
        val cleanText = text
            .replace(Regex("```[\\s\\S]*?```"), " Code block omitted. ")
            .replace(Regex("[#*_`>]"), "")
            .take(3500)
        if (cleanText.isBlank()) return

        val locale = when (language) {
            AssistantLanguage.ENGLISH -> Locale.US
            AssistantLanguage.HINDI -> Locale("hi", "IN")
            AssistantLanguage.ASSAMESE -> Locale("as", "IN")
        }
        val langResult = tts?.setLanguage(locale)
        if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts?.setLanguage(Locale("en", "IN"))
        }
        _speakingMessageId.value = messageId
        tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, messageId)
    }

    fun stopSpeaking() {
        tts?.stop()
        _speakingMessageId.value = null
    }

    fun startListening(
        language: AssistantLanguage,
        onPartialResult: (String) -> Unit,
        onFinalResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("Speech recognition service is not available on this device.")
            return
        }
        try {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _isListening.value = true
                    }

                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        _isListening.value = false
                    }

                    override fun onError(error: Int) {
                        _isListening.value = false
                        val msg = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required."
                            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network error during speech recognition."
                            SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected. Try speaking again."
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Listening timed out."
                            else -> "Speech recognition unavailable (code $error)."
                        }
                        onError(msg)
                    }

                    override fun onResults(results: Bundle?) {
                        _isListening.value = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull().orEmpty()
                        if (text.isNotBlank()) onFinalResult(text)
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull().orEmpty()
                        if (text.isNotBlank()) onPartialResult(text)
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.speechLocaleTag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            _isListening.value = true
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            _isListening.value = false
            onError(e.localizedMessage ?: "Failed to start voice input.")
        }
    }

    fun stopListening() {
        _isListening.value = false
        try {
            speechRecognizer?.stopListening()
        } catch (_: Exception) {
        }
    }

    fun release() {
        try {
            speechRecognizer?.destroy()
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        }
    }
}
