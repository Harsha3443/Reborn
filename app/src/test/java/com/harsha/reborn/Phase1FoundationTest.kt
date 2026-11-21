package com.harsha.reborn

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.harsha.reborn.core.engine.OemBatteryHelper
import com.harsha.reborn.core.engine.PermissionHealthManager
import com.harsha.reborn.core.engine.RebornPermissionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class Phase1FoundationTest {

    @Test
    fun `permission health manager reports all 5 core permission types`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = PermissionHealthManager(context)
        val snapshot = manager.evaluate()

        assertEquals(5, snapshot.totalCount)
        assertEquals(
            setOf(
                RebornPermissionType.ACCESSIBILITY,
                RebornPermissionType.USAGE_ACCESS,
                RebornPermissionType.OVERLAY,
                RebornPermissionType.NOTIFICATIONS,
                RebornPermissionType.BATTERY_OPTIMIZATION,
            ),
            snapshot.items.map { it.type }.toSet(),
        )
    }

    @Test
    fun `oem battery helper always provides at least one fallback intent and step`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val guidance = OemBatteryHelper.getGuidance(context)

        assertTrue(guidance.steps.isNotEmpty())
        assertTrue(guidance.deepLinkIntents.isNotEmpty())
    }
}
