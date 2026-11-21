package com.harsha.reborn

import android.app.Application
import androidx.work.Configuration
import com.harsha.reborn.core.engine.RulesCacheManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point for Reborn, initializing the Hilt dependency graph,
 * on-demand WorkManager configuration, and the reactive in-memory rule cache.
 */
@HiltAndroidApp
class RebornApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var rulesCacheManager: RulesCacheManager

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        rulesCacheManager.ensureStarted()
    }
}
