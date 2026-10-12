package com.almagribii.seremind.ui.onboarding

object RecommendedApps {
    val items = listOf(
        "com.zhiliaoapp.musically" to "TikTok",
        "com.instagram.android" to "Instagram",
        "com.google.android.youtube" to "YouTube",
        "com.twitter.android" to "Twitter/X",
        "com.facebook.katana" to "Facebook / Games"
    )

    val packageNames = items.map { it.first }
}
