package com.lagradost.cloudstream3.resume

import kotlin.jvm.JvmStatic

/** Additive, optional API. Stock hosts need not contain any classes from this package. */
const val NATIVE_RESUME_API_VERSION = 1

enum class ResumeCapability { READ_LIST, RESOLVE_RESUME, REPORT_PROGRESS, REMOVE_REMOTE_ITEM }

data class ResumeHostCapabilities(
    val apiVersion: Int,
    val capabilities: Set<ResumeCapability>,
) {
    fun supports(version: Int, required: Set<ResumeCapability>): Boolean =
        version == apiVersion && required.isNotEmpty() && capabilities.containsAll(required)
}

/** Host-derived ownership, never a website credential or a provider-chosen global namespace. */
data class ResumeSourceKey(val pluginId: String, val providerId: String) {
    init { require(pluginId.isNotBlank() && providerId.isNotBlank()) }
}

data class ResumeContext(val profileId: String, val accountId: String) {
    init { require(profileId.isNotBlank() && accountId.isNotBlank()) }
}

data class ResumeEpisode(
    val seriesId: String,
    val episodeId: String,
    val number: String? = null,
    val season: Int? = null,
    val audio: String? = null,
    val title: String? = null,
) {
    init {
        require(seriesId.isNotBlank() && episodeId.isNotBlank())
        require(season == null || season >= 0)
    }
}

/** Shared calculation for local/remote cards. Keep raw timestamps separate from visual rounding. */
data class ResumeProgress(val positionMs: Long, val durationMs: Long? = null) {
    init { require(positionMs >= 0 && (durationMs == null || durationMs > 0)) }
    val seekPositionMs: Long get() = durationMs?.let { positionMs.coerceAtMost(it) } ?: positionMs
    val fraction: Double? get() = durationMs?.let { seekPositionMs.toDouble() / it }
    val remainingMs: Long? get() = durationMs?.let { (it - seekPositionMs).coerceAtLeast(0) }
}

data class RemoteResumeEntry(
    val itemId: String,
    val accountId: String,
    val title: String,
    val detailUrl: String,
    val episode: ResumeEpisode,
    val progress: ResumeProgress?,
    val resumeToken: String,
    val posterUrl: String? = null,
    val backgroundUrl: String? = null,
    val episodePosterUrl: String? = null,
    val lastWatchedAtEpochMs: Long? = null,
    val completed: Boolean = false,
) {
    init {
        require(itemId.isNotBlank() && accountId.isNotBlank() && title.isNotBlank() && detailUrl.isNotBlank())
        require(resumeToken.length <= 16_384)
        require(lastWatchedAtEpochMs == null || lastWatchedAtEpochMs >= 0)
    }
}

data class ResumeListRequest(val context: ResumeContext, val cursor: String? = null, val limit: Int = 30) {
    init { require(limit in 1..100) }
}

enum class ResumeListState { READY, SIGNED_OUT, REAUTHENTICATION_REQUIRED }

data class ResumeListPage(
    val entries: List<RemoteResumeEntry>,
    val nextCursor: String? = null,
    val state: ResumeListState = ResumeListState.READY,
) {
    init { require(state == ResumeListState.READY || (entries.isEmpty() && nextCursor == null)) }
}

data class ResumeResolveRequest(val context: ResumeContext, val item: RemoteResumeEntry) {
    init { require(context.accountId == item.accountId) }
}

/** Resolve fresh episode data through the registered provider, never cache an expiring stream URL. */
data class ResolvedResume(
    val episode: ResumeEpisode,
    val detailUrl: String,
    val progress: ResumeProgress,
    val episodeData: String? = null,
    /** Identifies the requested saved episode when the provider chooses another audio edition. */
    val requestedEpisodeId: String = episode.episodeId,
) {
    init { require(detailUrl.isNotBlank() && requestedEpisodeId.isNotBlank()) }
}

enum class ResumePlaybackReason { PERIODIC, PAUSE, EXIT, ENDED, EPISODE_CHANGE, SEEK }

data class ResumePlaybackEvent(
    val context: ResumeContext,
    val itemId: String,
    val episode: ResumeEpisode,
    val sessionId: String,
    val sequence: Long,
    val progress: ResumeProgress,
    val reason: ResumePlaybackReason,
) {
    init { require(itemId.isNotBlank() && sessionId.isNotBlank() && sequence >= 0) }
}

enum class ResumeProgressReceipt { ACCEPTED, RETRY_LATER, REAUTHENTICATION_REQUIRED, UNSUPPORTED }

/** Implement only in an isolated extension adapter after host capability detection succeeds. */
interface ContinueWatchingSource {
    val apiVersion: Int get() = NATIVE_RESUME_API_VERSION
    val capabilities: Set<ResumeCapability>
    suspend fun getContinueWatching(request: ResumeListRequest): ResumeListPage
    suspend fun resolveResume(request: ResumeResolveRequest): ResolvedResume? = null
    suspend fun reportProgress(event: ResumePlaybackEvent): ResumeProgressReceipt = ResumeProgressReceipt.UNSUPPORTED
}

/**
 * The contract is present, but the host must not promise native UI/player support until wired in.
 * Extensions must check supports(), not merely Class.forName(), before disabling their fallback.
 */
object NativeResumeBridge {
    @JvmStatic
    fun getCapabilities(): ResumeHostCapabilities = ResumeHostCapabilities(NATIVE_RESUME_API_VERSION, emptySet())
}
