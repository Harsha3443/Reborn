package com.harsha.reborn

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.harsha.reborn.core.data.local.RebornDatabase
import com.harsha.reborn.core.data.local.SettingsRepository
import com.harsha.reborn.core.data.model.AppUsageSnapshot
import com.harsha.reborn.core.data.model.BlockDecision
import com.harsha.reborn.core.data.model.BlockReason
import com.harsha.reborn.core.data.model.BlockingRulesSnapshot
import com.harsha.reborn.core.data.model.BrowserKeywordRule
import com.harsha.reborn.core.data.model.ChargerCondition
import com.harsha.reborn.core.data.model.ConditionalRule
import com.harsha.reborn.core.data.model.DailyLimitAfterAction
import com.harsha.reborn.core.data.model.DailyTimeLimitRule
import com.harsha.reborn.core.data.model.DeviceConditionContext
import com.harsha.reborn.core.data.model.SmartRulesConfig
import com.harsha.reborn.core.data.repository.BlockingRulesRepository
import com.harsha.reborn.core.engine.RuleEvaluator
import com.harsha.reborn.core.engine.browser.BrowserKeywordDetector
import com.harsha.reborn.core.engine.feed.FeedNodeSnapshot
import com.harsha.reborn.core.engine.feed.FeedSelectorRegistry
import com.harsha.reborn.core.engine.feed.FeedWindowSnapshot
import com.harsha.reborn.core.engine.feed.InAppFeedDetector
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SmartRulesPhase5Test {

    private lateinit var context: Context
    private lateinit var db: RebornDatabase
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var rulesRepository: BlockingRulesRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, RebornDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        settingsRepository = SettingsRepository(context)
        rulesRepository = BlockingRulesRepository(
            dao = db.blockingRulesDao(),
            settingsRepository = settingsRepository,
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `conditional rule combines wifi, charging, overnight window, and optional location`() {
        val officeLat = 37.4220
        val officeLon = -122.0841
        val rule = ConditionalRule(
            id = 1L,
            name = "Deep Work Zone",
            packageNames = setOf("com.instagram.android"),
            requireWifi = true,
            wifiSsid = "OfficeWiFi",
            chargerCondition = ChargerCondition.CHARGING,
            useTimeWindow = true,
            startMinuteOfDay = 21 * 60, // 9:00 PM
            endMinuteOfDay = 7 * 60, // 7:00 AM
            useLocation = true,
            locationLabel = "Office",
            locationLat = officeLat,
            locationLon = officeLon,
            locationRadiusMeters = 300,
            isEnabled = true,
        )
        val snapshot = BlockingRulesSnapshot(conditionalRules = listOf(rule))
        val nightTime = LocalDateTime.of(2026, 11, 20, 22, 30)

        // All conditions satisfied
        val matchingDecision = RuleEvaluator.evaluate(
            packageName = "com.instagram.android",
            snapshot = snapshot,
            usageSnapshot = AppUsageSnapshot(),
            protectedPackages = setOf("com.android.dialer"),
            launchablePackages = setOf("com.instagram.android"),
            nowEpochMs = 1_000_000L,
            localDateTime = nightTime,
            conditionContext = DeviceConditionContext(
                isWifiConnected = true,
                wifiSsid = "OfficeWiFi",
                isCharging = true,
                latitude = 37.4225,
                longitude = -122.0843,
            ),
        )
        assertTrue(matchingDecision is BlockDecision.Block && matchingDecision.reason == BlockReason.CONDITIONAL_RULE)

        // Different Wi-Fi -> Allow
        val wrongWifi = RuleEvaluator.evaluate(
            packageName = "com.instagram.android",
            snapshot = snapshot,
            usageSnapshot = AppUsageSnapshot(),
            protectedPackages = emptySet(),
            launchablePackages = setOf("com.instagram.android"),
            nowEpochMs = 1_000_000L,
            localDateTime = nightTime,
            conditionContext = DeviceConditionContext(
                isWifiConnected = true,
                wifiSsid = "CoffeeShop",
                isCharging = true,
                latitude = officeLat,
                longitude = officeLon,
            ),
        )
        assertEquals(BlockDecision.Allow, wrongWifi)

        // Outside location radius -> Allow
        val farLocation = RuleEvaluator.evaluate(
            packageName = "com.instagram.android",
            snapshot = snapshot,
            usageSnapshot = AppUsageSnapshot(),
            protectedPackages = emptySet(),
            launchablePackages = setOf("com.instagram.android"),
            nowEpochMs = 1_000_000L,
            localDateTime = nightTime,
            conditionContext = DeviceConditionContext(
                isWifiConnected = true,
                wifiSsid = "OfficeWiFi",
                isCharging = true,
                latitude = 37.5000,
                longitude = -122.1500,
            ),
        )
        assertEquals(BlockDecision.Allow, farLocation)

        // Missing location permission -> fails safe without blocking
        val noLocation = RuleEvaluator.evaluate(
            packageName = "com.instagram.android",
            snapshot = snapshot,
            usageSnapshot = AppUsageSnapshot(),
            protectedPackages = emptySet(),
            launchablePackages = setOf("com.instagram.android"),
            nowEpochMs = 1_000_000L,
            localDateTime = nightTime,
            conditionContext = DeviceConditionContext(
                isWifiConnected = true,
                wifiSsid = "OfficeWiFi",
                isCharging = true,
                latitude = null,
                longitude = null,
            ),
        )
        assertEquals(BlockDecision.Allow, noLocation)
    }

    @Test
    fun `grayscale dim after daily limit dims limited app without blocking launcher`() {
        val snapshot = BlockingRulesSnapshot(
            dailyTimeLimits = listOf(
                DailyTimeLimitRule(
                    id = 1L,
                    name = "Video Cap",
                    packageNames = setOf("com.google.android.youtube"),
                    dailyLimitMinutes = 20,
                    afterLimitAction = DailyLimitAfterAction.GRAYSCALE_DIM,
                    isEnabled = true,
                )
            ),
        )
        val usage = AppUsageSnapshot(dailyMinutesByPackage = mapOf("com.google.android.youtube" to 25))
        val protectedPkgs = setOf("com.example", "com.android.launcher3")

        val appDecision = RuleEvaluator.evaluate(
            packageName = "com.google.android.youtube",
            snapshot = snapshot,
            usageSnapshot = usage,
            protectedPackages = protectedPkgs,
            launchablePackages = setOf("com.google.android.youtube"),
            nowEpochMs = 1_000_000L,
            localDateTime = LocalDateTime.of(2026, 11, 20, 14, 0),
        )
        assertEquals(BlockDecision.Allow, appDecision)

        val dimYoutube = RuleEvaluator.evaluateGrayscaleDim(
            packageName = "com.google.android.youtube",
            snapshot = snapshot,
            usageSnapshot = usage,
            protectedPackages = protectedPkgs,
        )
        assertTrue(dimYoutube.shouldDim)

        val dimLauncher = RuleEvaluator.evaluateGrayscaleDim(
            packageName = "com.android.launcher3",
            snapshot = snapshot,
            usageSnapshot = usage,
            protectedPackages = protectedPkgs,
        )
        assertFalse(dimLauncher.shouldDim)
    }

    @Test
    fun `in-app feed detector blocks Shorts and Reels, allows Home, and fails open on broken JSON`() {
        val catalog = FeedSelectorRegistry.defaultCatalog
        val enabled = SmartRulesConfig.DEFAULT_FEED_PROFILE_IDS

        // YouTube Home with unselected "Shorts" bottom tab must NOT block
        val youtubeHome = FeedWindowSnapshot(
            packageName = "com.google.android.youtube",
            nodes = listOf(
                FeedNodeSnapshot(viewIdResourceName = "com.google.android.youtube:id/search_button", text = "Search"),
                FeedNodeSnapshot(
                    viewIdResourceName = "com.google.android.youtube:id/shorts_pivot_item",
                    contentDescription = "Shorts",
                    isSelected = false,
                ),
            ),
        )
        assertNull(InAppFeedDetector.evaluateWindow(youtubeHome, catalog, enabled))

        // Active YouTube Shorts container -> Block
        val youtubeShorts = FeedWindowSnapshot(
            packageName = "com.google.android.youtube",
            nodes = listOf(
                FeedNodeSnapshot(viewIdResourceName = "com.google.android.youtube:id/reel_recycler"),
            ),
        )
        val shortsMatch = InAppFeedDetector.evaluateWindow(youtubeShorts, catalog, enabled)
        assertNotNull(shortsMatch)
        assertEquals("youtube_shorts", shortsMatch?.profileId)

        // Instagram Reels viewer -> Block, DM screen -> Allow
        val instagramReels = FeedWindowSnapshot(
            packageName = "com.instagram.android",
            nodes = listOf(
                FeedNodeSnapshot(viewIdResourceName = "com.instagram.android:id/clips_viewer_view_pager"),
            ),
        )
        assertEquals("instagram_reels", InAppFeedDetector.evaluateWindow(instagramReels, catalog, enabled)?.profileId)

        val instagramDms = FeedWindowSnapshot(
            packageName = "com.instagram.android",
            nodes = listOf(
                FeedNodeSnapshot(viewIdResourceName = "com.instagram.android:id/direct_inbox_list"),
            ),
        )
        assertNull(InAppFeedDetector.evaluateWindow(instagramDms, catalog, enabled))

        // Malformed selector JSON fails open (empty catalog -> never blocks)
        val brokenCatalog = FeedSelectorRegistry.loadCatalog("{ not valid json !!!")
        assertTrue(brokenCatalog.profiles.isEmpty())
        assertNull(InAppFeedDetector.evaluateWindow(youtubeShorts, brokenCatalog, enabled))

        // Custom selector JSON works without code changes
        val customJson = """
            {
              "schemaVersion": 1,
              "profiles": [
                {
                  "id": "custom_short_feed",
                  "displayName": "Custom Feed",
                  "packageNames": ["com.sample.videoapp"],
                  "activeContainerViewIds": ["vertical_endless_pager"]
                }
              ]
            }
        """.trimIndent()
        val customCatalog = FeedSelectorRegistry.loadCatalog(customJson)
        val customWindow = FeedWindowSnapshot(
            packageName = "com.sample.videoapp",
            nodes = listOf(FeedNodeSnapshot(viewIdResourceName = "com.sample.videoapp:id/vertical_endless_pager")),
        )
        assertEquals(
            "custom_short_feed",
            InAppFeedDetector.evaluateWindow(customWindow, customCatalog, setOf("custom_short_feed"))?.profileId,
        )
    }

    @Test
    fun `browser detector blocks configured domains and keywords and ignores placeholders`() {
        val rules = listOf(
            BrowserKeywordRule(id = 1L, pattern = "reddit.com", isDomain = true, isEnabled = true),
            BrowserKeywordRule(id = 2L, pattern = "breaking news", isDomain = false, isEnabled = true),
        )

        val redditHit = BrowserKeywordDetector.matchUrlOrKeyword("https://old.reddit.com/r/android", rules)
        assertEquals("reddit.com", redditHit?.rule?.pattern)

        val keywordHit = BrowserKeywordDetector.matchUrlOrKeyword("google.com/search?q=breaking+news", rules)
        assertEquals("breaking news", keywordHit?.rule?.pattern)
        val searchKeywordHit = BrowserKeywordDetector.matchUrlOrKeyword("breaking news live today", rules)
        assertEquals("breaking news", searchKeywordHit?.rule?.pattern)

        assertNull(BrowserKeywordDetector.matchUrlOrKeyword("Search or type web address", rules))
        assertNull(BrowserKeywordDetector.matchUrlOrKeyword("https://developer.android.com/compose", rules))
    }

    @Test
    fun `repository persists conditional rules, browser keyword rules, and grayscale daily limits`() = runBlocking {
        rulesRepository.saveConditionalRule(
            ConditionalRule(
                name = "Charging Social Lock",
                packageNames = setOf("com.zhiliaoapp.musically"),
                chargerCondition = ChargerCondition.CHARGING,
                isEnabled = true,
            )
        )
        rulesRepository.addBrowserKeywordRule("x.com", isDomain = true)
        rulesRepository.saveDailyTimeLimit(
            DailyTimeLimitRule(
                name = "Dim YouTube",
                packageNames = setOf("com.google.android.youtube"),
                dailyLimitMinutes = 15,
                afterLimitAction = DailyLimitAfterAction.GRAYSCALE_DIM,
                isEnabled = true,
            )
        )
        rulesRepository.setInAppFeedBlockingEnabled(true)
        rulesRepository.setGrayscaleDimOnLimitEnabled(true)

        val snapshot = rulesRepository.rulesSnapshotFlow.first()
        assertEquals(1, snapshot.conditionalRules.size)
        assertEquals("Charging Social Lock", snapshot.conditionalRules.first().name)
        assertEquals("x.com", snapshot.browserKeywordRules.first().pattern)
        assertEquals(DailyLimitAfterAction.GRAYSCALE_DIM, snapshot.dailyTimeLimits.first().afterLimitAction)
        assertTrue(snapshot.smartRulesConfig.inAppFeedBlockingEnabled)
        assertTrue(snapshot.smartRulesConfig.grayscaleDimOnLimitEnabled)
    }
}
