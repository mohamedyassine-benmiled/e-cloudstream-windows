package com.lagradost.cloudstream3.resume

import kotlinx.coroutines.test.runTest
import kotlin.test.*

class ResumeExtrasTest {
    private val owner = ResumeSourceKey("plugin", "provider")
    private val context = ResumeContext("profile", "account")
    private val item = RemoteResumeEntry("series", "account", "Title", "https://example.test/title",
        ResumeEpisode("series", "latest", "15", audio = "dub"), ResumeProgress(0), "latest")
    private fun extras(title: RemoteResumeEntry? = item, page: ResumeListPage = ResumeListPage(listOf(item))) = object : ContinueWatchingExtras {
        override val capabilities = setOf(ResumeCapability.READ_LIST, ResumeCapability.RESOLVE_RESUME, ResumeCapability.TITLE_PROGRESS, ResumeCapability.NEW_EPISODES)
        override suspend fun getContinueWatching(request: ResumeListRequest) = ResumeListPage(emptyList())
        override suspend fun getTitleProgress(context: ResumeContext, detailUrl: String) = title
        override suspend fun getNewEpisodes(request: ResumeListRequest) = page
    }
    private fun registry(host: Set<ResumeCapability> = extras().capabilities) = ResumeProviderRegistry(ResumeHostCapabilities(1, host))

    @Test fun extraCapabilitiesAreOptInAndOldHostsKeepTheOriginalApi() = runTest {
        val r = registry(setOf(ResumeCapability.READ_LIST, ResumeCapability.RESOLVE_RESUME))
        r.register(owner, extras())
        assertEquals(ResumeCallResult.Failure(ResumeFailure.UNSUPPORTED), r.getNewEpisodes(owner, ResumeListRequest(context)))
        assertIs<ResumeCallResult.Success<ResumeListPage>>(r.getContinueWatching(owner, ResumeListRequest(context)))
    }
    @Test fun titleProgressMustBelongToTheRequestedAccountAndTitle() = runTest {
        for (invalid in listOf(item.copy(accountId = "other"), item.copy(detailUrl = "https://example.test/other"))) {
            val r = registry(); r.register(owner, extras(title = invalid))
            assertEquals(ResumeCallResult.Failure(ResumeFailure.INVALID_RESPONSE), r.getTitleProgress(owner, context, item.detailUrl))
        }
        val r = registry(); r.register(owner, extras())
        assertEquals(item, assertIs<ResumeCallResult.Success<RemoteResumeEntry?>>(r.getTitleProgress(owner, context, item.detailUrl)).value)
    }
    @Test fun latestEpisodeCardsNeverCarryThePreviousEpisodeTimestamp() = runTest {
        val r = registry(); r.register(owner, extras(page = ResumeListPage(listOf(item.copy(progress = ResumeProgress(42_000))))))
        assertEquals(ResumeCallResult.Failure(ResumeFailure.INVALID_RESPONSE), r.getNewEpisodes(owner, ResumeListRequest(context)))
    }
    @Test fun wrongAccountsAndCursorLoopsAreRejectedAndUnloadRemovesExtras() = runTest {
        for (page in listOf(ResumeListPage(listOf(item.copy(accountId = "other"))), ResumeListPage(listOf(item), "same"))) {
            val r = registry(); r.register(owner, extras(page = page))
            assertEquals(ResumeCallResult.Failure(ResumeFailure.INVALID_RESPONSE), r.getNewEpisodes(owner, ResumeListRequest(context, "same")))
        }
        val r = registry(); r.register(owner, extras()); r.unregisterPlugin(owner.pluginId)
        assertEquals(ResumeCallResult.Failure(ResumeFailure.NOT_REGISTERED), r.getTitleProgress(owner, context, item.detailUrl))
    }
}
