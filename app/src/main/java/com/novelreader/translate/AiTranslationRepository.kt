package com.novelreader.translate

import com.novelreader.core.db.TranslationEntity
import com.novelreader.core.db.TranslationsDao
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Translates reader paragraphs via [AiTranslationApi].
 * One chapter = ONE request (no batching) to minimize request count;
 * retry with exponential backoff; persistent Room cache
 * (per novel+chapter+lang+model).
 */
class AiTranslationRepository(private val dao: TranslationsDao) {

    companion object {
        const val MAX_RETRIES = 2
    }

    sealed class Progress {
        data object Idle : Progress()
        data class Running(val doneRequests: Int, val totalRequests: Int) : Progress()
        data class Done(val fromCache: Boolean) : Progress()
        data class Error(val message: String) : Progress()
    }

    suspend fun getCached(
        novelId: String,
        chapterId: String,
        lang: String,
        model: String,
    ): TranslationEntity? = withContext(Dispatchers.IO) {
        runCatching { dao.find(novelId, chapterId, lang, model) }.getOrNull()
    }

    suspend fun invalidate(novelId: String, chapterId: String) = withContext(Dispatchers.IO) {
        runCatching { dao.deleteChapter(novelId, chapterId) }
    }

    /**
     * Translate ALL [paragraphs] of a chapter in a single request into
     * [targetLang]. Returns the translated list, same order and size.
     * Throws on final failure.
     */
    suspend fun translateParagraphs(
        api: AiTranslationApi,
        novelId: String,
        chapterId: String,
        paragraphs: List<String>,
        targetLang: String,
        onProgress: (Progress) -> Unit,
    ): List<String> {
        onProgress(Progress.Running(0, 1))
        val translated = withRetry {
            api.translateBatch(paragraphs, targetLang)
        }
        onProgress(Progress.Running(1, 1))
        onProgress(Progress.Done(fromCache = false))
        return translated
    }

    suspend fun store(
        novelId: String,
        chapterId: String,
        lang: String,
        model: String,
        paragraphs: List<String>,
    ) = withContext(Dispatchers.IO) {
        // reader consumes plain paragraphs; join with <p> so the cache stays
        // renderable by HtmlText if needed
        val html = paragraphs.joinToString("") { "<p>${it}</p>" }
        runCatching {
            dao.upsert(
                TranslationEntity(
                    id = "$novelId|$chapterId|$lang|$model",
                    novelId = novelId,
                    chapterId = chapterId,
                    targetLang = lang,
                    model = model,
                    translatedHtml = html,
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    private suspend fun <T> withRetry(block: suspend () -> T): T {
        var lastError: Exception? = null
        repeat(MAX_RETRIES + 1) { attempt ->
            try {
                return block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                if (attempt < MAX_RETRIES) delay(1000L * (1 shl attempt)) // 1s, 2s
            }
        }
        throw lastError ?: IllegalStateException("Unknown translation error")
    }
}
