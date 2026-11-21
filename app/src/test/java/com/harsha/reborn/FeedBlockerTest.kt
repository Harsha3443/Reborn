package com.harsha.reborn

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.harsha.reborn.core.data.local.BlockEventEntity
import com.harsha.reborn.core.data.local.SettingsRepository
import com.harsha.reborn.core.data.model.BlockReason
import com.harsha.reborn.core.data.model.SmartRulesConfig
import com.harsha.reborn.core.data.model.StrictModeConfig
import com.harsha.reborn.core.engine.InsightsCalculator
import com.harsha.reborn.core.engine.feed.FeedBlockCheckOutcome
import com.harsha.reborn.core.engine.feed.FeedBlocker
import com.harsha.reborn.core.engine.feed.FeedBlockerEngine
import com.harsha.reborn.core.engine.feed.FeedNodeSnapshot
import com.harsha.reborn.core.engine.feed.FeedSelectorRegistry
import com.harsha.reborn.core.engine.feed.FeedWindowSnapshot
import com.harsha.reborn.core.engine.feed.InAppFeedDetector
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FeedBlockerTest {

    private lateinit var context: Context
    private lateinit var settingsRepository: SettingsRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        settingsRepository = SettingsRepository(context)
    }

    @Test
    fun `asset feed_selectors json loads all four required target apps and DM selectors`() {
        val catalog = FeedSelectorRegistry.loadFromAsset(context)
        val byId = catalog.profiles.associateBy { it.id }

        assertTrue("youtube_shorts" in byId)
        assertTrue("instagram_reels" in byId)
        assertTrue("facebook_reels" in byId)
        assertTrue("snapchat_spotlight" in byId)

        assertEquals(listOf("com.google.android.youtube"), byId.getValue("youtube_shorts").packageNames)
        assertEquals(listOf("com.instagram.android"), byId.getValue("instagram_reels").packageNames)
        assertEquals(listOf("com.facebook.katana"), byId.getValue("facebook_reels").packageNames)
        assertEquals(listOf("com.snapchat.android"), byId.getValue("snapchat_spotlight").packageNames)

        assertTrue(byId.getValue("instagram_reels").dmContextViewIds.isNotEmpty())
        assertTrue(byId.getValue("facebook_reels").dmContextViewIds.isNotEmpty())
        assertTrue(byId.getValue("snapchat_spotlight").dmContextViewIds.isNotEmpty())
    }

    @Test
    fun `selector matching blocks all four apps when active and ignores unrelated or unselected tabs`() {
        val catalog = FeedSelectorRegistry.loadFromAsset(context)
        val enabled = SmartRulesConfig.CORE_FEED_PROFILE_IDS.toSet()

        // 1. YouTube Shorts (container view ID + selected tab, ignores unselected tab)
        val ytUnselected = FeedWindowSnapshot(
            packageName = "com.google.android.youtube",
            nodes = listOf(
                FeedNodeSnapshot(
                    viewIdResourceName = "com.google.android.youtube:id/shorts_pivot_item",
                    contentDescription = "Shorts",
                    isSelected = false,
                ),
            ),
        )
        assertNull(InAppFeedDetector.evaluateWindow(ytUnselected, catalog, enabled))

        val ytSelectedTab = FeedWindowSnapshot(
            packageName = "com.google.android.youtube",
            nodes = listOf(
                FeedNodeSnapshot(
                    viewIdResourceName = "com.google.android.youtube:id/shorts_pivot_item",
                    contentDescription = "Shorts",
                    isSelected = true,
                ),
            ),
        )
        val ytTabMatch = InAppFeedDetector.evaluateWindow(ytSelectedTab, catalog, enabled)
        assertEquals("youtube_shorts", ytTabMatch?.profileId)
        assertTrue(ytTabMatch?.isTabSelection == true)

        val ytPlayer = FeedWindowSnapshot(
            packageName = "com.google.android.youtube",
            nodes = listOf(FeedNodeSnapshot(viewIdResourceName = "com.google.android.youtube:id/reel_recycler")),
        )
        assertEquals("youtube_shorts", InAppFeedDetector.evaluateWindow(ytPlayer, catalog, enabled)?.profileId)

        // 2. Instagram Reels (container view ID + marker clues >= 2; 1 clue is ignored)
        val igSingleClue = FeedWindowSnapshot(
            packageName = "com.instagram.android",
            nodes = listOf(FeedNodeSnapshot(text = "Original audio")),
        )
        assertNull(InAppFeedDetector.evaluateWindow(igSingleClue, catalog, enabled))

        val igDualClues = FeedWindowSnapshot(
            packageName = "com.instagram.android",
            nodes = listOf(
                FeedNodeSnapshot(text = "Original audio"),
                FeedNodeSnapshot(contentDescription = "Remix this reel"),
            ),
        )
        assertEquals("instagram_reels", InAppFeedDetector.evaluateWindow(igDualClues, catalog, enabled)?.profileId)

        // 3. Facebook Reels
        val fbReels = FeedWindowSnapshot(
            packageName = "com.facebook.katana",
            nodes = listOf(FeedNodeSnapshot(viewIdResourceName = "com.facebook.katana:id/reels_viewer")),
        )
        assertEquals("facebook_reels", InAppFeedDetector.evaluateWindow(fbReels, catalog, enabled)?.profileId)

        // 4. Snapchat Spotlight
        val snapSpotlight = FeedWindowSnapshot(
            packageName = "com.snapchat.android",
            nodes = listOf(FeedNodeSnapshot(viewIdResourceName = "com.snapchat.android:id/spotlight_viewer")),
        )
        assertEquals("snapchat_spotlight", InAppFeedDetector.evaluateWindow(snapSpotlight, catalog, enabled)?.profileId)

        // Unrelated package must never be matched or scanned
        assertFalse(FeedBlocker.isTargetPackage("com.android.settings", catalog))
        assertTrue(FeedBlocker.isTargetPackage("com.instagram.android", catalog))
        val unrelatedWindow = FeedWindowSnapshot(
            packageName = "com.android.settings",
            nodes = listOf(FeedNodeSnapshot(viewIdResourceName = "com.google.android.youtube:id/reel_recycler")),
        )
        assertNull(InAppFeedDetector.evaluateWindow(unrelatedWindow, catalog, enabled))
    }

    @Test
    fun `allowReelsFromDms permits DM-launched reels when enabled, blocks when disabled, and blocks Reels tab`() {
        val catalog = FeedSelectorRegistry.loadFromAsset(context)
        val dmThreadWindow = FeedWindowSnapshot(
            packageName = "com.instagram.android",
            nodes = listOf(
                FeedNodeSnapshot(viewIdResourceName = "com.instagram.android:id/direct_thread"),
                FeedNodeSnapshot(text = "Sent a reel"),
            ),
        )
        val reelsViewerWindow = FeedWindowSnapshot(
            packageName = "com.instagram.android",
            nodes = listOf(
                FeedNodeSnapshot(viewIdResourceName = "com.instagram.android:id/clips_viewer_view_pager"),
            ),
        )
        val reelsTabWindow = FeedWindowSnapshot(
            packageName = "com.instagram.android",
            nodes = listOf(
                FeedNodeSnapshot(
                    viewIdResourceName = "com.instagram.android:id/clips_tab",
                    contentDescription = "Reels",
                    isSelected = true,
                ),
            ),
        )

        // When allowReelsFromDms = false -> DM -> Reels viewer still blocks
        val strictEngine = FeedBlockerEngine(debounceMs = 300L, cooldownMs = 2_500L, dmGraceMs = 20_000L)
        val strictConfig = SmartRulesConfig(
            inAppFeedBlockingEnabled = true,
            enabledFeedProfileIds = setOf("instagram_reels"),
            allowReelsFromDms = false,
        )
        assertEquals(
            FeedBlockCheckOutcome.Allow,
            strictEngine.evaluateWindowSnapshot("com.instagram.android", dmThreadWindow, catalog, strictConfig, 1_000L),
        )
        val strictOutcome = strictEngine.evaluateWindowSnapshot(
            packageName = "com.instagram.android",
            snapshot = reelsViewerWindow,
            catalog = catalog,
            config = strictConfig,
            nowElapsedMs = 2_000L,
        )
        assertTrue(strictOutcome is FeedBlockCheckOutcome.Block)

        // When allowReelsFromDms = true -> DM -> Reels viewer is allowed within grace window
        val dmEngine = FeedBlockerEngine(debounceMs = 300L, cooldownMs = 2_500L, dmGraceMs = 20_000L)
        val dmConfig = strictConfig.copy(allowReelsFromDms = true)
        assertEquals(
            FeedBlockCheckOutcome.Allow,
            dmEngine.evaluateWindowSnapshot("com.instagram.android", dmThreadWindow, catalog, dmConfig, 1_000L),
        )
        assertEquals(
            FeedBlockCheckOutcome.Allow,
            dmEngine.evaluateWindowSnapshot("com.instagram.android", reelsViewerWindow, catalog, dmConfig, 2_500L),
        )

        // Explicit Reels tab selection still blocks even right after a DM screen
        val tabOutcome = dmEngine.evaluateWindowSnapshot(
            packageName = "com.instagram.android",
            snapshot = reelsTabWindow,
            catalog = catalog,
            config = dmConfig,
            nowElapsedMs = 3_500L,
        )
        assertTrue(tabOutcome is FeedBlockCheckOutcome.Block)
    }

    @Test
    fun `debounce and back cooldown prevent Back loops and fail open on errors`() {
        val catalog = FeedSelectorRegistry.loadFromAsset(context)
        val config = SmartRulesConfig(
            inAppFeedBlockingEnabled = true,
            enabledFeedProfileIds = setOf("youtube_shorts"),
        )
        val engine = FeedBlockerEngine(debounceMs = 380L, cooldownMs = 2_500L, dmGraceMs = 20_000L)

        val homeWindow = FeedWindowSnapshot(
            packageName = "com.google.android.youtube",
            nodes = listOf(FeedNodeSnapshot(viewIdResourceName = "com.google.android.youtube:id/search_button")),
        )
        val shortsWindow = FeedWindowSnapshot(
            packageName = "com.google.android.youtube",
            nodes = listOf(FeedNodeSnapshot(viewIdResourceName = "com.google.android.youtube:id/reel_recycler")),
        )

        // Initial scan at t=1000ms allows Home, rapid follow-up at t=1150ms is debounced
        assertEquals(
            FeedBlockCheckOutcome.Allow,
            engine.evaluateWindowSnapshot("com.google.android.youtube", homeWindow, catalog, config, 1_000L),
        )
        assertEquals(
            FeedBlockCheckOutcome.Debounced,
            engine.evaluateWindowSnapshot("com.google.android.youtube", shortsWindow, catalog, config, 1_150L),
        )

        // After debounce window (t=1500ms) Shorts triggers Block + enters Back cooldown
        val blocked = engine.evaluateWindowSnapshot("com.google.android.youtube", shortsWindow, catalog, config, 1_500L)
        assertTrue(blocked is FeedBlockCheckOutcome.Block)
        assertEquals("youtube_shorts", (blocked as FeedBlockCheckOutcome.Block).match.profileId)

        // Immediate events inside cooldown return CooldownActive (no Back loop)
        assertEquals(
            FeedBlockCheckOutcome.CooldownActive,
            engine.evaluateWindowSnapshot("com.google.android.youtube", shortsWindow, catalog, config, 2_200L),
        )
        assertEquals(
            FeedBlockCheckOutcome.CooldownActive,
            engine.evaluateWindowSnapshot("com.google.android.youtube", shortsWindow, catalog, config, 3_900L),
        )

        // After cooldown expires (t=4000ms), detection works again
        val reblocked = engine.evaluateWindowSnapshot("com.google.android.youtube", shortsWindow, catalog, config, 4_000L)
        assertTrue(reblocked is FeedBlockCheckOutcome.Block)

        // Empty nodes / disabled config / malformed JSON all fail open to Allow
        assertEquals(
            FeedBlockCheckOutcome.Allow,
            engine.evaluateWindowSnapshot(
                "com.google.android.youtube",
                FeedWindowSnapshot("com.google.android.youtube", emptyList()),
                catalog,
                config,
                10_000L,
            ),
        )
        assertTrue(FeedSelectorRegistry.validateCatalogJson("{bad json").isFailure)
        assertTrue(FeedSelectorRegistry.loadCatalog("{bad json").profiles.isEmpty())
    }

    @Test
    fun `tree traversal respects maxDepth and maxNodes limits and fails open on null or throwing nodes`() {
        val catalog = FeedSelectorRegistry.loadFromAsset(context)

        // Build a 16-level chain where target node sits at depth 15 (> DEFAULT_MAX_DEPTH = 12)
        val deepLeaf = FakeNodeSource(viewIdResourceName = "com.google.android.youtube:id/reel_recycler")
        var tooDeepRoot = deepLeaf
        repeat(15) {
            tooDeepRoot = FakeNodeSource(viewIdResourceName = "com.google.android.youtube:id/wrapper_$it", children = listOf(tooDeepRoot))
        }

        val depthLimitedSnapshot = InAppFeedDetector.captureNodeSource(
            root = tooDeepRoot,
            packageName = "com.google.android.youtube",
            maxNodes = 140,
            maxDepth = 12,
        )
        assertEquals(13, depthLimitedSnapshot.nodes.size) // depth 0..12 inclusive
        assertNull(
            InAppFeedDetector.evaluateWindow(
                depthLimitedSnapshot,
                catalog,
                setOf("youtube_shorts"),
            ),
        )

        // When node is within depth 10 (<= 12), it is captured and matched
        var withinDepthRoot = FakeNodeSource(viewIdResourceName = "com.google.android.youtube:id/reel_recycler")
        repeat(10) {
            withinDepthRoot = FakeNodeSource(viewIdResourceName = "com.google.android.youtube:id/wrapper_$it", children = listOf(withinDepthRoot))
        }
        val capturedSnapshot = InAppFeedDetector.captureNodeSource(
            root = withinDepthRoot,
            packageName = "com.google.android.youtube",
            maxNodes = 140,
            maxDepth = 12,
        )
        assertEquals(11, capturedSnapshot.nodes.size)
        assertNotNull(
            InAppFeedDetector.evaluateWindow(
                capturedSnapshot,
                catalog,
                setOf("youtube_shorts"),
            ),
        )

        // Null root fails open with empty snapshot
        val nullSnapshot = InAppFeedDetector.captureSnapshot(null, "com.google.android.youtube")
        assertTrue(nullSnapshot.nodes.isEmpty())

        // Exception inside node traversal fails open instead of crashing
        val throwingNode = object : InAppFeedDetector.NodeSource {
            override val viewIdResourceName: String? get() = throw IllegalStateException("recycled")
            override val text: CharSequence? get() = null
            override val contentDescription: CharSequence? get() = null
            override val isSelected: Boolean get() = false
            override val isVisibleToUser: Boolean get() = true
            override val childCount: Int get() = 0
            override fun getChild(index: Int): InAppFeedDetector.NodeSource? = null
        }
        val safeSnapshot = InAppFeedDetector.captureNodeSource(throwingNode, "com.google.android.youtube")
        assertTrue(safeSnapshot.nodes.isEmpty())
    }

    @Test
    fun `DataStore persistence, strict lock check, and Insights counting work for Feed Blocking`() = runBlocking {
        settingsRepository.updateFeedBlockingState(
            enabled = true,
            enabledProfileIds = setOf("youtube_shorts", "instagram_reels"),
        )
        settingsRepository.setAllowReelsFromDms(true)

        val prefs = settingsRepository.preferencesFlow.first()
        assertTrue(prefs.smartRulesConfig.inAppFeedBlockingEnabled)
        assertEquals(setOf("youtube_shorts", "instagram_reels"), prefs.smartRulesConfig.enabledFeedProfileIds)
        assertTrue(prefs.smartRulesConfig.allowReelsFromDms)

        // Strict Mode lock guard check
        val activeStrict = StrictModeConfig(
            isActive = true,
            startedAtEpochMs = 1_000L,
            endsAtEpochMs = 61_000L,
        )
        assertTrue(activeStrict.isEffectivelyLocked(10_000L))
        assertFalse(activeStrict.isEffectivelyLocked(62_000L))

        // Insights counts IN_APP_FEED block events
        val zone = ZoneId.of("UTC")
        val today = LocalDate.of(2026, 11, 21)
        val noonMs = today.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val insights = InsightsCalculator.buildDashboardSnapshot(
            today = today,
            zoneId = zone,
            usageAccessGranted = false,
            todayMinutesByPackage = emptyMap(),
            dailyScreenTimeMinutesByDate = emptyMap(),
            appLabelsByPackage = mapOf("com.instagram.android" to "Instagram"),
            excludedPackages = emptySet(),
            blockEvents = listOf(
                BlockEventEntity(
                    packageName = "com.instagram.android",
                    appLabel = "Instagram",
                    reason = BlockReason.IN_APP_FEED.name,
                    timestampEpochMs = noonMs,
                ),
                BlockEventEntity(
                    packageName = "com.google.android.youtube",
                    appLabel = "YouTube",
                    reason = BlockReason.IN_APP_FEED.name,
                    timestampEpochMs = noonMs + 5_000L,
                ),
            ),
            unlockPasses = emptyList(),
            persistedRebornDatesIso = emptySet(),
            persistedBestStreak = 0,
            hasAnyActiveProtectionToday = true,
        )
        assertEquals(2, insights.blockedTodayCount)
        assertEquals(2, insights.totalWeeklyBlockedCount)
    }

    private class FakeNodeSource(
        override val viewIdResourceName: String? = null,
        override val text: CharSequence? = null,
        override val contentDescription: CharSequence? = null,
        override val isSelected: Boolean = false,
        override val isVisibleToUser: Boolean = true,
        private val children: List<FakeNodeSource> = emptyList(),
    ) : InAppFeedDetector.NodeSource {
        override val childCount: Int get() = children.size
        override fun getChild(index: Int): InAppFeedDetector.NodeSource? = children.getOrNull(index)
    }
}
