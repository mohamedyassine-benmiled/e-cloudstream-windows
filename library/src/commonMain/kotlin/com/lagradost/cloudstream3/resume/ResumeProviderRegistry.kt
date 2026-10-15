package com.lagradost.cloudstream3.resume

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withTimeoutOrNull

enum class ResumeFailure { NOT_REGISTERED, UNSUPPORTED, TIMEOUT, INVALID_RESPONSE, PROVIDER_ERROR }

sealed interface ResumeCallResult<out T> {
    data class Success<T>(val value: T) : ResumeCallResult<T>
    data class Failure(val reason: ResumeFailure) : ResumeCallResult<Nothing>
}

fun interface ResumeRegistration { fun close() }

/**
 * Host-owned registry. It stores handlers, not account history, secrets, or a playback outbox.
 * Host integration supplies verified plugin/provider ownership and calls unregisterPlugin on unload.
 */
class ResumeProviderRegistry(
    private val host: ResumeHostCapabilities,
    private val timeoutMs: Long = 20_000,
) {
    private class Record(val source: ContinueWatchingSource, val capabilities: Set<ResumeCapability>) {
        val calls = MutableStateFlow<Set<Job>>(emptySet())
        fun track(job: Job, add: Boolean) {
            while (true) {
                val old = calls.value
                if (calls.compareAndSet(old, if (add) old + job else old - job)) return
            }
        }
    }
    private val records = MutableStateFlow<Map<ResumeSourceKey, Record>>(emptyMap())

    init { require(timeoutMs > 0) }

    fun registeredSources(): Set<ResumeSourceKey> = records.value.keys.toSet()

    fun register(owner: ResumeSourceKey, source: ContinueWatchingSource): ResumeRegistration {
        val capabilities = source.capabilities.toSet()
        require(source.apiVersion == NATIVE_RESUME_API_VERSION && source.apiVersion == host.apiVersion) {
            "Unsupported native resume API version"
        }
        require(ResumeCapability.READ_LIST in capabilities)
        require(host.supports(source.apiVersion, setOf(ResumeCapability.READ_LIST))) { "Native resume UI is not installed" }
        val record = Record(source, capabilities.intersect(host.capabilities))
        while (true) {
            val old = records.value
            require(owner !in old) { "A resume source is already registered for this provider" }
            if (records.compareAndSet(old, old + (owner to record))) break
        }
        return ResumeRegistration { remove { key, current -> key == owner && current === record } }
    }

    fun unregisterPlugin(pluginId: String) { remove { key, _ -> key.pluginId == pluginId } }

    private fun remove(predicate: (ResumeSourceKey, Record) -> Boolean) {
        while (true) {
            val old = records.value
            val next = old.filterNot { (key, value) -> predicate(key, value) }
            if (records.compareAndSet(old, next)) {
                old.filterKeys { it !in next }.values.forEach { record -> record.calls.value.forEach { it.cancel() } }
                return
            }
        }
    }

    suspend fun getContinueWatching(owner: ResumeSourceKey, request: ResumeListRequest): ResumeCallResult<ResumeListPage> =
        call(owner, ResumeCapability.READ_LIST) { source ->
            val page = source.getContinueWatching(request)
            require(page.entries.size <= request.limit)
            require(page.entries.all { it.accountId == request.context.accountId })
            require(page.entries.map { it.itemId }.distinct().size == page.entries.size)
            require(page.nextCursor == null || page.nextCursor != request.cursor)
            page.copy(entries = page.entries.toList())
        }

    suspend fun resolveResume(owner: ResumeSourceKey, request: ResumeResolveRequest): ResumeCallResult<ResolvedResume?> =
        call(owner, ResumeCapability.RESOLVE_RESUME) { source ->
            source.resolveResume(request)?.also { result ->
                require(result.episode.seriesId == request.item.episode.seriesId)
                require(result.requestedEpisodeId == request.item.episode.episodeId)
                require(result.episode.season == request.item.episode.season)
                require(result.episode.number == request.item.episode.number)
                if (result.episode.episodeId != request.item.episode.episodeId || result.episode.audio != request.item.episode.audio) {
                    // Edition preference is extension policy. Only the same logical episode may be substituted.
                    require(request.item.episode.number != null && result.episode.number == request.item.episode.number)
                }
            }
        }

    suspend fun reportProgress(owner: ResumeSourceKey, event: ResumePlaybackEvent): ResumeCallResult<ResumeProgressReceipt> =
        call(owner, ResumeCapability.REPORT_PROGRESS) { it.reportProgress(event) }

    private data class Box<T>(val value: T)

    private suspend fun <T> call(
        owner: ResumeSourceKey,
        capability: ResumeCapability,
        operation: suspend (ContinueWatchingSource) -> T,
    ): ResumeCallResult<T> {
        val record = records.value[owner] ?: return ResumeCallResult.Failure(ResumeFailure.NOT_REGISTERED)
        if (capability !in record.capabilities) return ResumeCallResult.Failure(ResumeFailure.UNSUPPORTED)
        return try {
            val result = coroutineScope {
                val task = async(start = CoroutineStart.LAZY) { operation(record.source) }
                record.track(task, true)
                try {
                    if (records.value[owner] !== record) {
                        task.cancel()
                        null
                    } else withTimeoutOrNull(timeoutMs) { Box(task.await()) }
                } finally {
                    task.cancel()
                    record.track(task, false)
                }
            }
                ?: return ResumeCallResult.Failure(if (records.value[owner] === record) ResumeFailure.TIMEOUT else ResumeFailure.NOT_REGISTERED)
            if (records.value[owner] !== record) ResumeCallResult.Failure(ResumeFailure.NOT_REGISTERED)
            else ResumeCallResult.Success(result.value)
        } catch (e: CancellationException) {
            currentCoroutineContext().ensureActive()
            if (records.value[owner] !== record) ResumeCallResult.Failure(ResumeFailure.NOT_REGISTERED)
            else throw e
        } catch (_: IllegalArgumentException) {
            ResumeCallResult.Failure(ResumeFailure.INVALID_RESPONSE)
        } catch (_: Exception) {
            // Provider messages can contain credentials/response bodies. Do not propagate them to UI/logs.
            ResumeCallResult.Failure(ResumeFailure.PROVIDER_ERROR)
        }
    }
}
