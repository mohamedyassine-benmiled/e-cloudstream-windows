package com.lagradost.desktop.resume

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getKey
import com.lagradost.cloudstream3.CloudStreamApp.Companion.setKey
import com.lagradost.cloudstream3.resume.*
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

data class RemoteResumeRequest(val owner: ResumeSourceKey, val context: ResumeContext, val item: RemoteResumeEntry, val revision: Long)
data class RemoteResumeLaunch(val request: RemoteResumeRequest, val resolved: ResolvedResume, val localEpisodeId: Int)

data class RemoteResumeCard(val request: RemoteResumeRequest) : SearchResponse {
    override val name = request.item.title
    override val url = request.item.detailUrl
    override val apiName = request.owner.providerId
    override var type: TvType? = TvType.entries.firstOrNull { it.name == request.item.mediaType } ?: TvType.Others
    override var posterUrl = request.item.posterUrl
    override var posterHeaders: Map<String, String>? = null
    override var id: Int? = null
    override var quality: SearchQuality? = null
    override var score: Score? = null
}

/** Same presentation inputs for the existing local card and extension-backed cards. */
data class ResumeCardState(val number: String?, val season: Int?, val audio: String?, val progress: ResumeProgress?)

fun SearchResponse.resumeCardState(): ResumeCardState? = when (this) {
    is RemoteResumeCard -> request.item.let { ResumeCardState(it.episode.number, it.episode.season, it.episode.audio, it.progress) }
    is DataStoreHelper.ResumeWatchingResult -> ResumeCardState(episode?.toString(), season, null,
        watchPos?.let { if (it.position >= 0 && it.duration > 0) ResumeProgress(it.position, it.duration) else null })
    else -> null
}

internal fun matchesNativeEpisode(target: ResolvedResume, dub: DubStatus, episode: ResultEpisode): Boolean {
    val audio = when (target.episode.audio?.lowercase()) {
        "sub", "subbed" -> DubStatus.Subbed
        "dub", "dubbed" -> DubStatus.Dubbed
        null, "none" -> null
        else -> if (target.episodeData == null) return false else null
    }
    if (audio != null && audio != dub) return false
    return if (target.episodeData != null) episode.data == target.episodeData
        else target.episode.number != null && episode.episode.toString() == target.episode.number && episode.season == target.episode.season
}

/** Preserve the existing player's completion/restart policy for both local and remote resume. */
internal fun initialResumePosition(positionMs: Long, durationMs: Long?): Long {
    if (durationMs == null) return positionMs
    if (durationMs <= 0 || positionMs * 100L / durationMs > 95L) return 0L
    return positionMs
}

data class NativeResumeShelf(
    val owner: ResumeSourceKey,
    val context: ResumeContext? = null,
    val cards: List<RemoteResumeCard> = emptyList(),
    val nextCursor: String? = null,
    val state: ResumeListState = ResumeListState.READY,
    val error: ResumeFailure? = null,
)

/** Remote cache is independent of local history; only explicit Hide writes a separate preference. */
object NativeContinueWatching {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val permits = Semaphore(3)
    private val more = ConcurrentHashMap.newKeySet<ResumeSourceKey>()
    private val state = MutableStateFlow<List<NativeResumeShelf>>(emptyList())
    val shelves = state.asStateFlow()

    init {
        scope.launch {
            combine(NativeResumeBridge.changes, DataStoreHelper.selectedAccountNumberFlow) { revision, _ -> revision }
                .collectLatest { revision ->
                    state.value = emptyList()
                    val profile = DataStoreHelper.currentAccount
                    val sources = NativeResumeBridge.registeredSources().filter(::providerStillLoaded).sortedBy { it.providerId }
                    val result = coroutineScope {
                        sources.map { owner -> async { permits.withPermit { firstPage(owner, profile, revision) } } }.awaitAll()
                    }
                    if (revision == NativeResumeBridge.changes.value && profile == DataStoreHelper.currentAccount) state.value = result
                }
        }
    }

    fun refresh() = NativeResumeBridge.invalidate()

    private fun providerStillLoaded(owner: ResumeSourceKey): Boolean =
        APIHolder.getApiFromNameNull(owner.providerId)?.sourcePlugin == owner.pluginId

    private suspend fun firstPage(owner: ResumeSourceKey, profile: String, revision: Long): NativeResumeShelf {
        return when (val account = NativeResumeBridge.getAccountId(owner, profile)) {
            is ResumeCallResult.Failure -> NativeResumeShelf(owner, error = account.reason)
            is ResumeCallResult.Success -> {
                val id = account.value ?: return NativeResumeShelf(owner, state = ResumeListState.SIGNED_OUT)
                val context = ResumeContext(profile, id)
                val result = NativeResumeBridge.getContinueWatching(owner, ResumeListRequest(context))
                val after = NativeResumeBridge.getAccountId(owner, profile)
                if (after !is ResumeCallResult.Success || after.value != id) {
                    return NativeResumeShelf(owner, state = ResumeListState.REAUTHENTICATION_REQUIRED)
                }
                when (result) {
                    is ResumeCallResult.Failure -> NativeResumeShelf(owner, context, error = result.reason)
                    is ResumeCallResult.Success -> NativeResumeShelf(owner, context,
                        result.value.entries.map { RemoteResumeCard(RemoteResumeRequest(owner, context, it, revision)) }.filterNot(::hidden),
                        result.value.nextCursor, result.value.state)
                }
            }
        }
    }

    fun loadMore(owner: ResumeSourceKey) {
        if (!more.add(owner)) return
        scope.launch {
            try {
                val old = state.value.firstOrNull { it.owner == owner } ?: return@launch
                val context = old.context ?: return@launch
                val cursor = old.nextCursor ?: return@launch
                val revision = NativeResumeBridge.changes.value
                permits.withPermit {
                    val account = NativeResumeBridge.getAccountId(owner, context.profileId)
                    if (account !is ResumeCallResult.Success || account.value != context.accountId) { refresh(); return@withPermit }
                    val result = NativeResumeBridge.getContinueWatching(owner, ResumeListRequest(context, cursor))
                    val after = NativeResumeBridge.getAccountId(owner, context.profileId)
                    if (after !is ResumeCallResult.Success || after.value != context.accountId) { refresh(); return@withPermit }
                    if (revision != NativeResumeBridge.changes.value || context.profileId != DataStoreHelper.currentAccount) return@withPermit
                    state.update { shelves -> shelves.map { shelf ->
                        if (shelf.owner != owner || shelf.context != context || shelf.nextCursor != cursor) shelf
                        else when (result) {
                            is ResumeCallResult.Failure -> shelf.copy(error = result.reason)
                            is ResumeCallResult.Success -> shelf.copy(
                                cards = (shelf.cards + result.value.entries.map {
                                    RemoteResumeCard(RemoteResumeRequest(owner, context, it, revision))
                                }).distinctBy { it.request.item.itemId }.filterNot(::hidden),
                                nextCursor = result.value.nextCursor, state = result.value.state, error = null)
                        }
                    } }
                }
            } finally { more.remove(owner) }
        }
    }

    private fun hideKey(card: RemoteResumeCard): String {
        val r = card.request
        // Length-prefix fields to avoid ambiguous concatenations or separator collisions.
        val identity = listOf(r.owner.pluginId, r.owner.providerId, r.context.accountId, r.item.itemId)
            .joinToString("") { "${it.length}:$it" }
        val hash = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray()).joinToString("") { "%02x".format(it) }
        return "${r.context.profileId}/native_resume_hidden/$hash"
    }

    private fun hidden(card: RemoteResumeCard) = getKey<Boolean>(hideKey(card), false) == true

    fun hide(card: RemoteResumeCard) {
        if (card.request.context.profileId != DataStoreHelper.currentAccount) return
        setKey(hideKey(card), true)
        state.update { shelves -> shelves.map { shelf -> shelf.copy(cards = shelf.cards.filterNot { it.request == card.request }) } }
    }

    fun isCurrent(request: RemoteResumeRequest): Boolean =
        request.context.profileId == DataStoreHelper.currentAccount && request.revision == NativeResumeBridge.changes.value &&
            request.owner in NativeResumeBridge.registeredSources() && providerStillLoaded(request.owner)

    suspend fun resolve(request: RemoteResumeRequest): ResumeCallResult<ResolvedResume?> = withContext(Dispatchers.IO) {
        if (!isCurrent(request)) return@withContext ResumeCallResult.Failure(ResumeFailure.NOT_REGISTERED)
        val account = NativeResumeBridge.getAccountId(request.owner, request.context.profileId)
        if (account !is ResumeCallResult.Success || account.value != request.context.accountId) {
            refresh()
            return@withContext ResumeCallResult.Failure(ResumeFailure.NOT_REGISTERED)
        }
        val result = NativeResumeBridge.resolveResume(request.owner, ResumeResolveRequest(request.context, request.item))
        val after = NativeResumeBridge.getAccountId(request.owner, request.context.profileId)
        if (isCurrent(request) && after is ResumeCallResult.Success && after.value == request.context.accountId) result
        else ResumeCallResult.Failure(ResumeFailure.NOT_REGISTERED)
    }
}
