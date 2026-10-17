package com.lagradost.desktop.resume

import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.resume.ResolvedResume
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.ui.result.VideoWatchState

/** Ephemeral projection only: advancing the frontier never writes fabricated local watches. */
internal fun cloudEpisodeProgress(target: ResolvedResume, anchor: ResultEpisode, anchorDub: DubStatus,
    dub: DubStatus, episode: ResultEpisode): ResultEpisode {
    if (dub != anchorDub || episode.parentId != anchor.parentId || episode.apiName != anchor.apiName ||
        episode.seasonIndex != anchor.seasonIndex) return episode
    return when {
        episode.episode < anchor.episode -> episode.copy(position = episode.duration.coerceAtLeast(0), videoWatchState = VideoWatchState.Watched)
        episode.id == anchor.id -> {
            val duration = target.progress.durationMs ?: episode.duration
            val position = target.progress.seekPositionMs
            episode.copy(position = position, duration = duration,
                videoWatchState = if (duration > 0 && position.toDouble() / duration >= 0.95) VideoWatchState.Watched else VideoWatchState.None)
        }
        else -> episode.copy(position = 0, videoWatchState = VideoWatchState.None)
    }
}
