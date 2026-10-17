package com.lagradost.desktop.resume

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.resume.*
import com.lagradost.cloudstream3.utils.DataStoreHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap

data class RemoteTitleProgress(val request: RemoteResumeRequest, val resolved: ResolvedResume)

/** Optional rows and title state live only in memory. No fetching action writes local history. */
object NativeResumeExtras {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val state = MutableStateFlow<List<NativeResumeShelf>>(emptyList())
    val shelves = state.asStateFlow()
    private val loading = ConcurrentHashMap.newKeySet<ResumeSourceKey>()

    init {
        scope.launch {
            combine(NativeResumeBridge.changes, DataStoreHelper.selectedAccountNumberFlow) { revision, profile -> revision to profile }
                .collectLatest { (revision, _) ->
                    state.value = emptyList()
                    val profile = DataStoreHelper.currentAccount
                    val owners = NativeResumeBridge.registeredSources().filter {
                        loaded(it) && NativeResumeBridge.supports(it, ResumeCapability.NEW_EPISODES)
                    }
                    // Bound concurrency without allowing one provider to suppress another provider's row.
                    for (owner in owners) {
                        val shelf = fetch(owner, profile, revision)
                        if (revision == NativeResumeBridge.changes.value && profile == DataStoreHelper.currentAccount)
                            state.update { it + shelf }
                    }
                }
        }
    }

    private fun loaded(owner: ResumeSourceKey) = APIHolder.getApiFromNameNull(owner.providerId)?.sourcePlugin == owner.pluginId
    private suspend fun account(owner: ResumeSourceKey, profile: String): String? =
        (NativeResumeBridge.getAccountId(owner, profile) as? ResumeCallResult.Success)?.value

    private suspend fun fetch(owner: ResumeSourceKey, profile: String, revision: Long,
        previous: NativeResumeShelf? = null): NativeResumeShelf {
        val id = account(owner, profile) ?: return NativeResumeShelf(owner, state = ResumeListState.REAUTHENTICATION_REQUIRED, newEpisodes = true)
        val context = ResumeContext(profile, id)
        if (previous?.context != null && previous.context != context)
            return NativeResumeShelf(owner, state = ResumeListState.REAUTHENTICATION_REQUIRED, newEpisodes = true)
        val result = NativeResumeBridge.getNewEpisodes(owner, ResumeListRequest(context, previous?.nextCursor))
        if (account(owner, profile) != id) return NativeResumeShelf(owner, state = ResumeListState.REAUTHENTICATION_REQUIRED, newEpisodes = true)
        return when (result) {
            is ResumeCallResult.Failure -> (previous ?: NativeResumeShelf(owner, context, newEpisodes = true)).copy(error = result.reason)
            is ResumeCallResult.Success -> {
                val page = result.value
                val consumed = previous?.consumedCursors.orEmpty() + listOfNotNull(previous?.nextCursor)
                if (page.nextCursor != null && page.nextCursor in consumed)
                    (previous ?: NativeResumeShelf(owner, context, newEpisodes = true)).copy(nextCursor = null, error = ResumeFailure.INVALID_RESPONSE)
                else NativeResumeShelf(owner, context,
                    (previous?.cards.orEmpty() + page.entries.map { RemoteResumeCard(RemoteResumeRequest(owner, context, it, revision)) })
                        .distinctBy { it.request.item.itemId }.filterNot(NativeContinueWatching::hidden),
                    page.nextCursor, page.state, newEpisodes = true, consumedCursors = consumed)
            }
        }
    }

    suspend fun loadMore(owner: ResumeSourceKey) {
        if (!loading.add(owner)) return
        try {
            val old = state.value.firstOrNull { it.owner == owner } ?: return
            val cursor = old.nextCursor ?: return
            val revision = NativeResumeBridge.changes.value
            val profile = DataStoreHelper.currentAccount
            if (old.context?.profileId != profile) return
            val next = fetch(owner, profile, revision, old)
            if (revision == NativeResumeBridge.changes.value && profile == DataStoreHelper.currentAccount)
                state.update { shelves -> shelves.map { if (it === old) next else it } }
        } finally { loading.remove(owner) }
    }

    fun hide(card: RemoteResumeCard) { state.update { shelves -> shelves.map { it.copy(cards = it.cards.filterNot { c -> c.request == card.request }) } } }

    suspend fun titleProgress(provider: String, detailUrl: String): RemoteTitleProgress? {
        val plugin = APIHolder.getApiFromNameNull(provider)?.sourcePlugin ?: return null
        val owner = NativeResumeBridge.registeredSources().singleOrNull {
            it.providerId == provider && it.pluginId == plugin && NativeResumeBridge.supports(it, ResumeCapability.TITLE_PROGRESS)
        } ?: return null
        val profile = DataStoreHelper.currentAccount
        val revision = NativeResumeBridge.changes.value
        val id = account(owner, profile) ?: return null
        val context = ResumeContext(profile, id)
        val item = (NativeResumeBridge.getTitleProgress(owner, context, detailUrl) as? ResumeCallResult.Success)?.value ?: return null
        val request = RemoteResumeRequest(owner, context, item, revision)
        if (!NativeContinueWatching.isCurrent(request) || account(owner, profile) != id) return null
        val target = (NativeContinueWatching.resolve(request) as? ResumeCallResult.Success)?.value ?: return null
        return if (NativeContinueWatching.isCurrent(request)) RemoteTitleProgress(request, target) else null
    }
}
