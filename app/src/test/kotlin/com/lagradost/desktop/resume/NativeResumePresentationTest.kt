package com.lagradost.desktop.resume

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.resume.*
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.ui.result.VideoWatchState
import com.lagradost.cloudstream3.utils.DataStoreHelper
import kotlin.test.*

class NativeResumePresentationTest {
    @Test fun accountShelfAppearsOnlyInsideItsOwningProvider() {
        val owner = ResumeSourceKey("plugin-a", "provider-a")
        assertTrue(owner.belongsToHomeProvider("provider-a", "plugin-a"))
        assertFalse(owner.belongsToHomeProvider("provider-b", "plugin-a"))
        assertFalse(owner.belongsToHomeProvider("provider-a", "plugin-b"))
        assertFalse(owner.belongsToHomeProvider("provider-a", null))
        assertFalse(owner.belongsToHomeProvider(null, null))
    }

    private val episode = ResumeEpisode("series", "episode-sub", "12", 1, "sub")
    private fun remote() = RemoteResumeCard(RemoteResumeRequest(ResumeSourceKey("plugin", "provider"), ResumeContext("0", "account"),
        RemoteResumeEntry("item", "account", "Title", "detail", episode, ResumeProgress(59_123, 120_000), "opaque", mediaType = "Anime"), 0))
    private fun local() = DataStoreHelper.ResumeWatchingResult("Title", "detail", "provider", TvType.Anime, null,
        DataStoreHelper.PosDur(59_123, 120_000), 12, 1, 12, 1, false)
    private fun playable(data: String = "episode-data", season: Int? = 1) = ResultEpisode(
        headerName = "Title", name = "Episode 12", poster = null, episode = 12, seasonIndex = null, season = season,
        data = data, apiName = "provider", id = 12, index = 11, position = 0, duration = 0, score = null,
        description = null, isFiller = null, tvType = TvType.Anime, parentId = 1, videoWatchState = VideoWatchState.None)

    @Test fun remoteAndLocalUseTheSameEpisodeAndProgressPresentation() {
        val a = local().resumeCardState()!!
        val b = remote().resumeCardState()!!
        assertEquals(a.number, b.number)
        assertEquals(a.season, b.season)
        assertEquals(a.progress, b.progress)
        assertEquals(60_877, b.progress!!.remainingMs)
        assertEquals(TvType.Anime, remote().type)
        assertNull(remote().id) // no remote record is assigned a fake local history ID
    }

    @Test fun knownAndUnknownDurationKeepTheExistingResumePolicy() {
        assertEquals(59_123, initialResumePosition(59_123, 120_000))
        assertEquals(59_123, initialResumePosition(59_123, null))
        assertEquals(0, initialResumePosition(59_123, 0))
        assertEquals(95_999, initialResumePosition(95_999, 100_000))
        assertEquals(0, initialResumePosition(96_000, 100_000))
    }

    @Test fun exactEpisodeDataAndAudioSelectTheRequestedEdition() {
        val resolved = ResolvedResume(episode, "detail", ResumeProgress(59_123), episodeData = "episode-data")
        assertTrue(matchesNativeEpisode(resolved, DubStatus.Subbed, playable()))
        assertFalse(matchesNativeEpisode(resolved, DubStatus.Dubbed, playable()))
        assertFalse(matchesNativeEpisode(resolved, DubStatus.Subbed, playable("another-episode")))
    }

    @Test fun numberFallbackNeverMixesSeasonsOrGuessesUnknownAudio() {
        val resolved = ResolvedResume(episode, "detail", ResumeProgress(59_123))
        assertTrue(matchesNativeEpisode(resolved, DubStatus.Subbed, playable()))
        assertFalse(matchesNativeEpisode(resolved, DubStatus.Subbed, playable(season = 2)))
        assertFalse(matchesNativeEpisode(resolved.copy(episode = episode.copy(audio = "unknown")), DubStatus.Subbed, playable()))
    }

    @Test fun specialNumbersRequireExplicitEpisodeDataRatherThanRounding() {
        val resolved = ResolvedResume(episode.copy(number = "12.5"), "detail", ResumeProgress(0))
        assertFalse(matchesNativeEpisode(resolved, DubStatus.Subbed, playable()))
        assertTrue(matchesNativeEpisode(resolved.copy(episodeData = "episode-data"), DubStatus.Subbed, playable()))
    }
}
