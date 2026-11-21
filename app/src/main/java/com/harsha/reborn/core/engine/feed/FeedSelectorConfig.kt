package com.harsha.reborn.core.engine.feed

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class FeedSelectorProfile(
    val id: String,
    val displayName: String,
    val packageNames: List<String>,
    val activeContainerViewIds: List<String> = emptyList(),
    val selectedTabViewIds: List<String> = emptyList(),
    val selectedTabLabels: List<String> = emptyList(),
    val markerTextClues: List<String> = emptyList(),
    val minMarkerCluesRequired: Int = 2,
    val dmContextViewIds: List<String> = emptyList(),
    val dmContextTextClues: List<String> = emptyList(),
)

@Serializable
data class FeedSelectorCatalog(
    val schemaVersion: Int = 1,
    val profiles: List<FeedSelectorProfile> = emptyList(),
)

/**
 * Config-driven selector catalog stored in `assets/feed_selectors.json` (and custom-importable
 * via JSON) so view-ID / label updates for YouTube, Instagram, Facebook, Snapchat, etc.
 * can be edited or swapped without rewriting Kotlin logic.
 * Fails open to an empty catalog if a custom JSON payload is malformed.
 */
object FeedSelectorRegistry {

    const val ASSET_FILE_NAME = "feed_selectors.json"

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
    }

    val DEFAULT_SELECTOR_JSON: String = """
        {
          "schemaVersion": 1,
          "profiles": [
            {
              "id": "youtube_shorts",
              "displayName": "YouTube Shorts",
              "packageNames": ["com.google.android.youtube"],
              "activeContainerViewIds": [
                "reel_recycler",
                "reel_player_page_container",
                "reel_watch_fragment_root",
                "reel_player_overlay_container",
                "shorts_player_container"
              ],
              "selectedTabViewIds": [
                "shorts_pivot_item",
                "pivot_shorts"
              ],
              "selectedTabLabels": ["shorts"],
              "markerTextClues": ["remix this short", "shorts player"],
              "minMarkerCluesRequired": 1,
              "dmContextViewIds": [],
              "dmContextTextClues": []
            },
            {
              "id": "instagram_reels",
              "displayName": "Instagram Reels",
              "packageNames": ["com.instagram.android"],
              "activeContainerViewIds": [
                "clips_viewer_view_pager",
                "clips_video_container",
                "clips_media_component",
                "clips_ufi_component"
              ],
              "selectedTabViewIds": [
                "clips_tab"
              ],
              "selectedTabLabels": ["reels"],
              "markerTextClues": ["use audio", "original audio", "remix this reel"],
              "minMarkerCluesRequired": 2,
              "dmContextViewIds": [
                "direct_inbox_list",
                "direct_thread",
                "thread_fragment",
                "direct_reply_bar",
                "direct_story_reply",
                "message_content_container"
              ],
              "dmContextTextClues": [
                "direct message",
                "sent a reel",
                "sent you a reel",
                "reply to"
              ]
            },
            {
              "id": "facebook_reels",
              "displayName": "Facebook Reels",
              "packageNames": ["com.facebook.katana"],
              "activeContainerViewIds": [
                "reels_viewer",
                "fb_shorts_viewer",
                "reels_tab_container"
              ],
              "selectedTabViewIds": [
                "reels_tab"
              ],
              "selectedTabLabels": ["reels"],
              "markerTextClues": ["reels", "add a comment"],
              "minMarkerCluesRequired": 2,
              "dmContextViewIds": [
                "messenger_thread",
                "thread_view",
                "message_container"
              ],
              "dmContextTextClues": [
                "messenger",
                "sent a reel"
              ]
            },
            {
              "id": "snapchat_spotlight",
              "displayName": "Snapchat Spotlight",
              "packageNames": ["com.snapchat.android"],
              "activeContainerViewIds": [
                "spotlight_viewer",
                "spotlight_opera_container"
              ],
              "selectedTabViewIds": [
                "ngs_spotlight_tab"
              ],
              "selectedTabLabels": ["spotlight"],
              "markerTextClues": ["spotlight", "trending sounds"],
              "minMarkerCluesRequired": 2,
              "dmContextViewIds": [
                "chat_list",
                "chat_message_content",
                "chat_input_bar"
              ],
              "dmContextTextClues": [
                "send a chat",
                "replied to your snap"
              ]
            },
            {
              "id": "tiktok_feed",
              "displayName": "TikTok Feed",
              "packageNames": [
                "com.zhiliaoapp.musically",
                "com.ss.android.ugc.trill",
                "com.ss.android.ugc.aweme"
              ],
              "activeContainerViewIds": [
                "viewpager",
                "feed_container",
                "video_view"
              ],
              "selectedTabViewIds": [],
              "selectedTabLabels": ["for you", "following", "friends"],
              "markerTextClues": ["for you", "following", "add comment"],
              "minMarkerCluesRequired": 2,
              "dmContextViewIds": [],
              "dmContextTextClues": []
            }
          ]
        }
    """.trimIndent()

    val defaultCatalog: FeedSelectorCatalog by lazy {
        runCatching {
            jsonParser.decodeFromString<FeedSelectorCatalog>(DEFAULT_SELECTOR_JSON)
        }.getOrElse { FeedSelectorCatalog() }
    }

    fun loadFromAsset(
        context: Context,
        assetFileName: String = ASSET_FILE_NAME,
    ): FeedSelectorCatalog {
        val rawAsset = runCatching {
            context.assets.open(assetFileName).bufferedReader().use { it.readText() }
        }.getOrNull()
        if (rawAsset.isNullOrBlank()) return defaultCatalog
        return runCatching {
            val parsed = jsonParser.decodeFromString<FeedSelectorCatalog>(rawAsset)
            if (parsed.profiles.isEmpty()) defaultCatalog else parsed
        }.getOrDefault(defaultCatalog)
    }

    fun loadCatalog(
        customJson: String?,
        fallbackCatalog: FeedSelectorCatalog = defaultCatalog,
    ): FeedSelectorCatalog {
        val candidate = customJson?.trim().orEmpty()
        if (candidate.isEmpty()) return fallbackCatalog
        // Fail-open on broken user/updated config rather than crashing or blocking falsely.
        return runCatching {
            val parsed = jsonParser.decodeFromString<FeedSelectorCatalog>(candidate)
            if (parsed.profiles.isEmpty()) fallbackCatalog else parsed
        }.getOrDefault(FeedSelectorCatalog())
    }

    fun validateCatalogJson(rawJson: String): Result<FeedSelectorCatalog> = runCatching {
        val parsed = jsonParser.decodeFromString<FeedSelectorCatalog>(rawJson.trim())
        require(parsed.profiles.isNotEmpty()) { "Catalog must include at least one feed profile." }
        parsed
    }
}
