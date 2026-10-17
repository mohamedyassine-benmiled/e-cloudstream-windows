package com.lagradost.desktop.resume

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.resume.*
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.ui.result.VideoWatchState
import kotlin.test.*

class CloudEpisodeProgressTest {
    private fun ep(number: Int, season: Int? = 1) = ResultEpisode("Title", "Episode $number", null, number,
        season, season, "data-$number", "provider", number, number - 1, 20_000, 120_000, null, null, null,
        TvType.Anime, 123, VideoWatchState.None)
    private val anchor = ep(12)
    private val target = ResolvedResume(ResumeEpisode("series", "ep12", "12", 1, "sub"), "detail", ResumeProgress(59_123, 120_000), "data-12")

    @Test fun earlierEpisodesAreInferredWatchedWithoutMutatingInput() {
        val earlier = ep(3)
        val result = cloudEpisodeProgress(target, anchor, DubStatus.Subbed, DubStatus.Subbed, earlier)
        assertEquals(VideoWatchState.Watched, result.videoWatchState)
        assertEquals(120_000, result.position)
        assertEquals(VideoWatchState.None, earlier.videoWatchState)
        assertEquals(20_000, earlier.position)
    }
    @Test fun frontierUsesExactCloudTimestampAndFutureIgnoresLocalProgress() {
        assertEquals(59_123, cloudEpisodeProgress(target, anchor, DubStatus.Subbed, DubStatus.Subbed, anchor).position)
        val future = ep(13).copy(videoWatchState = VideoWatchState.Watched)
        val result = cloudEpisodeProgress(target, anchor, DubStatus.Subbed, DubStatus.Subbed, future)
        assertEquals(0, result.position)
        assertEquals(VideoWatchState.None, result.videoWatchState)
    }
    @Test fun neverMixesTitlesSeasonsOrAudioEditions() {
        val otherSeason = ep(3, 2)
        assertSame(otherSeason, cloudEpisodeProgress(target, anchor, DubStatus.Subbed, DubStatus.Subbed, otherSeason))
        val earlier = ep(3)
        assertSame(earlier, cloudEpisodeProgress(target, anchor, DubStatus.Subbed, DubStatus.Dubbed, earlier))
        val otherTitle = earlier.copy(parentId = 124)
        assertSame(otherTitle, cloudEpisodeProgress(target, anchor, DubStatus.Subbed, DubStatus.Subbed, otherTitle))
    }
    @Test fun unknownDurationIsNotInvented() {
        val unknown = anchor.copy(duration = 0)
        val result = cloudEpisodeProgress(target.copy(progress = ResumeProgress(59_123)), unknown, DubStatus.Subbed, DubStatus.Subbed, unknown)
        assertEquals(0, result.duration)
        assertEquals(59_123, result.position)
    }
}
