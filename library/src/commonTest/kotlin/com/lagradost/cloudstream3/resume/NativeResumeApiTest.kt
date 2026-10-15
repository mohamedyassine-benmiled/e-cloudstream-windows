package com.lagradost.cloudstream3.resume

import kotlin.test.*

class NativeResumeApiTest {
    @Test fun nativeUiIsNotAdvertisedBeforeHostIntegration() {
        assertFalse(NativeResumeBridge.getCapabilities().supports(1, setOf(ResumeCapability.READ_LIST)))
    }

    @Test fun capabilitiesRequireAnExactVersionAndAllRequestedFeatures() {
        val host = ResumeHostCapabilities(1, setOf(ResumeCapability.READ_LIST))
        assertTrue(host.supports(1, setOf(ResumeCapability.READ_LIST)))
        assertFalse(host.supports(2, setOf(ResumeCapability.READ_LIST)))
        assertFalse(host.supports(1, setOf(ResumeCapability.RESOLVE_RESUME)))
        assertFalse(host.supports(1, emptySet()))
    }

    @Test fun exactTimestampsAreNotRoundedToVisualPercentages() {
        val progress = ResumeProgress(59_123, 120_000)
        assertEquals(59_123, progress.seekPositionMs)
        assertEquals(60_877, progress.remainingMs)
        assertEquals(59_123.0 / 120_000, progress.fraction)
    }

    @Test fun unknownDurationDoesNotInventAProgressBar() {
        val progress = ResumeProgress(59_123)
        assertNull(progress.fraction)
        assertNull(progress.remainingMs)
        assertEquals(59_123, progress.seekPositionMs)
    }

    @Test fun overshootIsClampedWithoutRewritingTheSavedPosition() {
        val progress = ResumeProgress(120_500, 120_000)
        assertEquals(120_500, progress.positionMs)
        assertEquals(120_000, progress.seekPositionMs)
        assertEquals(0, progress.remainingMs)
        assertEquals(1.0, progress.fraction)
    }

    @Test fun invalidProgressAndBlankIdentitiesAreRejected() {
        assertFailsWith<IllegalArgumentException> { ResumeProgress(-1) }
        assertFailsWith<IllegalArgumentException> { ResumeProgress(0, 0) }
        assertFailsWith<IllegalArgumentException> { ResumeSourceKey("", "provider") }
        assertFailsWith<IllegalArgumentException> { ResumeContext("profile", "") }
        assertFailsWith<IllegalArgumentException> { ResumeListRequest(ResumeContext("p", "a"), limit = 0) }
    }

    @Test fun episodesCanRepresentSpecialsWithoutConfusingThemWithIntegerIds() {
        val episode = ResumeEpisode("series", "opaque-special", "2.5", audio = "sub")
        assertEquals("2.5", episode.number)
        assertEquals("opaque-special", episode.episodeId)
    }

    @Test fun signedOutIsDistinctFromAnEmptyReadyList() {
        assertNotEquals(ResumeListPage(emptyList()), ResumeListPage(emptyList(), state = ResumeListState.SIGNED_OUT))
        assertFailsWith<IllegalArgumentException> {
            ResumeListPage(emptyList(), nextCursor = "2", state = ResumeListState.SIGNED_OUT)
        }
    }

    @Test fun resolveCannotUseAnotherAccountsEntry() {
        val entry = RemoteResumeEntry("item", "a", "Title", "detail", ResumeEpisode("s", "e"), null, "opaque")
        assertFailsWith<IllegalArgumentException> { ResumeResolveRequest(ResumeContext("p", "b"), entry) }
    }
}
