package com.novelreader.translate

import com.novelreader.core.db.TranslationEntity
import com.novelreader.core.db.TranslationsDao
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Translates reader paragraphs via [AiTranslationApi] with:
 *  - paragraph-level batching ([BATCH_SIZE] paragraphs per request)
 *  - bounded parallelism ([MAX_CONCURRENT] requests in flight)
 *  - retry with exponential backoff
 *  - persistent Room cache (per novel+chapter+lang+model)
 */
class AiTranslationRepository(private val dao: TranslationsDao) {

    companion object {
        const val BATCH_SIZE = 16
        const val MAX_CONCURRENT = 4
        const val MAX_RETRIES = 2
    }

    sealed class Progress {
        data object Idle : Progress()
        data class Running(val doneBatches: Int, val totalBatches: Int) : Progress()
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
     * Translate [paragraphs] (reader paragraph list) into [targetLang].
     * Returns the translated list, same order and size. Throws on final failure.
     */
    suspend fun translateParagraphs(
        api: AiTranslationApi,
        novelId: String,
        chapterId: String,
        paragraphs: List<String>,
        targetLang: String,
        onProgress: (Progress) -> Unit,
    ): List<String> = coroutineScope {
        val batches = paragraphs.chunked(BATCH_SIZE)
        onProgress(Progress.Running(0, batches.size))

        val semaphore = Semaphore(MAX_CONCURRENT)
        val done = java.util.concurrent.atomic.AtomicInteger(0)

        val translated: List<List<String>> = batches.map { batch ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    val result = withRetry {
                        api.translateBatch(batch, targetLang)
                    }
                    onProgress(Progress.Running(done.incrementAndGet(), batches.size))
                    result
                }
            }
        }.awaitAll()

        onProgress(Progress.Done(fromCache = false))
        translated.flatten()
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
