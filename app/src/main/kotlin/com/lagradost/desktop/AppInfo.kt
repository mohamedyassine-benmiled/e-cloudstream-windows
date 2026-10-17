package com.lagradost.desktop

import com.lagradost.cloudstream3.BuildConfig

/**
 * What the app says about itself and where its people are. New versions come from the GitHub releases of [REPO]; the updater
 * (update/UpdateCheck.kt) downloads and installs them from there.
 */
object AppInfo {
    const val REPO = "mohamedyassine-benmiled/e-cloudstream-windows"
    const val REPO_URL = "https://github.com/$REPO"
    const val RELEASES_URL = "$REPO_URL/releases"
    const val LATEST_RELEASE_URL = RELEASES_URL
    const val ISSUES_URL = "$REPO_URL/issues"

    /** News, new versions, help and feedback */
    const val TELEGRAM_URL = "https://t.me/cs3d_official"

    /** The community server: chat, help, ideas */
    const val DISCORD_URL = "https://discord.gg/u82JU5JDM"
    const val DONATE_URL = "https://razorpay.me/@lelouch11"

    val version: String = BuildConfig.DESKTOP_VERSION

    /** Fork packages remain experimental prereleases even without a version suffix. */
    val isPreRelease: Boolean get() = true
}
