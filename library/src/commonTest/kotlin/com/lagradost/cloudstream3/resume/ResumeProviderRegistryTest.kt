@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.lagradost.cloudstream3.resume

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

class ResumeProviderRegistryTest {
    private val all = setOf(ResumeCapability.READ_LIST, ResumeCapability.RESOLVE_RESUME, ResumeCapability.REPORT_PROGRESS)
    private val host = ResumeHostCapabilities(1, all)
    private val owner = ResumeSourceKey("plugin-a", "provider")
    private val context = ResumeContext("profile", "account")
    private val entry = RemoteResumeEntry("item", "account", "Title", "detail", ResumeEpisode("s", "sub-e", "12", 1, "sub"), ResumeProgress(59_123, 120_000), "opaque")
    private fun source(
        version: Int = 1,
        capabilities: Set<ResumeCapability> = all,
        list: suspend (ResumeListRequest) -> ResumeListPage = { ResumeListPage(listOf(entry)) },
        resolve: suspend (ResumeResolveRequest) -> ResolvedResume? = {
            ResolvedResume(it.item.episode, it.item.detailUrl, it.item.progress!!)
        },
    ) = object : ContinueWatchingSource {
        override val apiVersion = version
        override val capabilities = capabilities
        override suspend fun getContinueWatching(request: ResumeListRequest) = list(request)
        override suspend fun resolveResume(request: ResumeResolveRequest) = resolve(request)
        override suspend fun reportProgress(event: ResumePlaybackEvent) = ResumeProgressReceipt.ACCEPTED
    }

    @Test fun registrationRequiresACompatibleEnabledHost() {
        assertFailsWith<IllegalArgumentException> { ResumeProviderRegistry(NativeResumeBridge.getCapabilities()).register(owner, source()) }
        assertFailsWith<IllegalArgumentException> { ResumeProviderRegistry(host).register(owner, source(version = 2)) }
        assertFailsWith<IllegalArgumentException> { ResumeProviderRegistry(host).register(owner, source(capabilities = emptySet())) }
    }

    @Test fun listPreservesRemoteOrderAndExplicitAccountScope() = runTest {
        val second = entry.copy(itemId = "second")
        val registry = ResumeProviderRegistry(host)
        registry.register(owner, source(list = { request ->
            assertEquals(context, request.context)
            ResumeListPage(listOf(second, entry), "next")
        }))
        val result = assertIs<ResumeCallResult.Success<ResumeListPage>>(registry.getContinueWatching(owner, ResumeListRequest(context)))
        assertEquals(listOf("second", "item"), result.value.entries.map { it.itemId })
        assertEquals("next", result.value.nextCursor)
    }

    @Test fun malformedAccountDuplicateItemsAndCursorLoopsAreRejected() = runTest {
        val invalid = listOf(
            ResumeListPage(listOf(entry.copy(accountId = "another-account"))),
            ResumeListPage(listOf(entry, entry)),
            ResumeListPage(listOf(entry), "same"),
        )
        for (page in invalid) {
            val registry = ResumeProviderRegistry(host)
            registry.register(owner, source(list = { page }))
            assertEquals(ResumeCallResult.Failure(ResumeFailure.INVALID_RESPONSE),
                registry.getContinueWatching(owner, ResumeListRequest(context, "same")))
        }
    }

    @Test fun hostCapabilityIntersectionStopsUnsupportedOperations() = runTest {
        val registry = ResumeProviderRegistry(ResumeHostCapabilities(1, setOf(ResumeCapability.READ_LIST)))
        registry.register(owner, source())
        assertEquals(ResumeCallResult.Failure(ResumeFailure.UNSUPPORTED), registry.resolveResume(owner, ResumeResolveRequest(context, entry)))
    }

    @Test fun oneProviderFailureDoesNotExposeSecretsOrBreakAnotherSource() = runTest {
        val registry = ResumeProviderRegistry(host)
        registry.register(owner, source(list = { error("secret response body must not escape") }))
        val other = ResumeSourceKey("plugin-b", "provider")
        registry.register(other, source())
        assertEquals(ResumeCallResult.Failure(ResumeFailure.PROVIDER_ERROR), registry.getContinueWatching(owner, ResumeListRequest(context)))
        assertIs<ResumeCallResult.Success<ResumeListPage>>(registry.getContinueWatching(other, ResumeListRequest(context)))
    }

    @Test fun timeoutCancelsWorkAndDoesNotDestroyRegistration() = runTest {
        var cancelled = false
        val registry = ResumeProviderRegistry(host, timeoutMs = 50)
        registry.register(owner, source(list = {
            try { delay(10_000); ResumeListPage(listOf(entry)) } finally { cancelled = true }
        }))
        assertEquals(ResumeCallResult.Failure(ResumeFailure.TIMEOUT), registry.getContinueWatching(owner, ResumeListRequest(context)))
        assertTrue(cancelled)
        assertEquals(setOf(owner), registry.registeredSources())
    }

    @Test fun unloadCancelsOutstandingWorkAndDiscardsItsResult() = runTest {
        var cancelled = false
        val registry = ResumeProviderRegistry(host)
        registry.register(owner, source(list = {
            try { delay(10_000); ResumeListPage(listOf(entry)) } finally { cancelled = true }
        }))
        val result = async { registry.getContinueWatching(owner, ResumeListRequest(context)) }
        runCurrent()
        registry.unregisterPlugin(owner.pluginId)
        assertEquals(ResumeCallResult.Failure(ResumeFailure.NOT_REGISTERED), result.await())
        assertTrue(cancelled)
        assertTrue(registry.registeredSources().isEmpty())
    }

    @Test fun cancellationFromTheCallerIsNotSwallowed() = runTest {
        val registry = ResumeProviderRegistry(host)
        registry.register(owner, source(list = { delay(10_000); ResumeListPage(listOf(entry)) }))
        val result = async { registry.getContinueWatching(owner, ResumeListRequest(context)) }
        runCurrent()
        result.cancelAndJoin()
        assertTrue(result.isCancelled)
        assertEquals(setOf(owner), registry.registeredSources())
    }

    @Test fun oldRegistrationHandleCannotRemoveItsReplacement() = runTest {
        val registry = ResumeProviderRegistry(host)
        val old = registry.register(owner, source())
        assertFailsWith<IllegalArgumentException> { registry.register(owner, source()) }
        old.close()
        registry.register(owner, source())
        old.close()
        assertIs<ResumeCallResult.Success<ResumeListPage>>(registry.getContinueWatching(owner, ResumeListRequest(context)))
    }

    @Test fun extensionCanPreferAnotherAudioEditionOfTheSameEpisode() = runTest {
        val registry = ResumeProviderRegistry(host)
        // Choice is made inside the source, not by a new host preference.
        registry.register(owner, source(resolve = {
            ResolvedResume(it.item.episode.copy(episodeId = "dub-e", audio = "dub"), "detail",
                ResumeProgress(59_123, 120_000), requestedEpisodeId = it.item.episode.episodeId)
        }))
        val result = assertIs<ResumeCallResult.Success<ResolvedResume?>>(registry.resolveResume(owner, ResumeResolveRequest(context, entry)))
        val resolved = assertNotNull(result.value)
        assertEquals("dub", resolved.episode.audio)
        assertEquals(59_123, resolved.progress.seekPositionMs)
    }

    @Test fun alternateAudioCannotChangeTheEpisodeOrSeason() = runTest {
        for (episode in listOf(entry.episode.copy(episodeId = "wrong", number = "11", audio = "dub"),
            entry.episode.copy(season = 2), entry.episode.copy(number = "11"))) {
            val registry = ResumeProviderRegistry(host)
            registry.register(owner, source(resolve = {
                ResolvedResume(episode, "detail", ResumeProgress(0), requestedEpisodeId = entry.episode.episodeId)
            }))
            assertEquals(ResumeCallResult.Failure(ResumeFailure.INVALID_RESPONSE), registry.resolveResume(owner, ResumeResolveRequest(context, entry)))
        }
    }
}
