package com.harsha.reborn.core.data.model

enum class ChargerCondition {
    ANY,
    CHARGING,
    NOT_CHARGING,
}

data class ConditionalRule(
    val id: Long = 0L,
    val name: String,
    val packageNames: Set<String>,
    val requireWifi: Boolean = false,
    val wifiSsid: String = "",
    val chargerCondition: ChargerCondition = ChargerCondition.ANY,
    val useTimeWindow: Boolean = false,
    val startMinuteOfDay: Int = 21 * 60,
    val endMinuteOfDay: Int = 7 * 60,
    val useLocation: Boolean = false,
    val locationLabel: String = "",
    val locationLat: Double = 0.0,
    val locationLon: Double = 0.0,
    val locationRadiusMeters: Int = 300,
    val isEnabled: Boolean = true,
) {
    fun hasAnyCondition(): Boolean =
        requireWifi || chargerCondition != ChargerCondition.ANY || useTimeWindow || useLocation
}

data class BrowserKeywordRule(
    val id: Long = 0L,
    val pattern: String,
    val isDomain: Boolean = true,
    val isEnabled: Boolean = true,
)

data class SmartRulesConfig(
    val inAppFeedBlockingEnabled: Boolean = false,
    val enabledFeedProfileIds: Set<String> = DEFAULT_FEED_PROFILE_IDS,
    val allowReelsFromDms: Boolean = false,
    val customFeedSelectorsJson: String = "",
    val browserBlockingEnabled: Boolean = true,
    val grayscaleDimOnLimitEnabled: Boolean = false,
) {
    companion object {
        val DEFAULT_FEED_PROFILE_IDS = setOf(
            "youtube_shorts",
            "instagram_reels",
            "tiktok_feed",
            "facebook_reels",
            "snapchat_spotlight",
        )

        val CORE_FEED_PROFILE_IDS = listOf(
            "youtube_shorts",
            "instagram_reels",
            "facebook_reels",
            "snapchat_spotlight",
        )
    }
}

data class DeviceConditionContext(
    val isWifiConnected: Boolean = false,
    val wifiSsid: String? = null,
    val isCharging: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

data class GrayscaleDimDecision(
    val shouldDim: Boolean,
    val label: String = "",
)
