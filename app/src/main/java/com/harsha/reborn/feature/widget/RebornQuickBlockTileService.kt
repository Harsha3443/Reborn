package com.harsha.reborn.feature.widget

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.example.MainActivity
import com.example.R
import com.harsha.reborn.core.engine.EngineEntryPoint
import com.harsha.reborn.core.engine.QuickBlockToggleOutcome
import com.harsha.reborn.core.engine.QuickBlockWidgetState
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class RebornQuickBlockTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartListening() {
        super.onStartListening()
        scope.launch {
            runCatching {
                val controller = EntryPointAccessors.fromApplication(
                    applicationContext,
                    EngineEntryPoint::class.java,
                ).quickBlockToggleController()
                val state = controller.readCurrentState()
                applyStateToTile(state)
            }
        }
    }

    override fun onClick() {
        super.onClick()
        scope.launch {
            val controller = EntryPointAccessors.fromApplication(
                applicationContext,
                EngineEntryPoint::class.java,
            ).quickBlockToggleController()

            when (controller.toggleQuickBlock()) {
                QuickBlockToggleOutcome.NoAppsSelected -> openRebornApp()
                else -> Unit
            }

            val updated = controller.readCurrentState()
            applyStateToTile(updated)
            QuickBlockWidgetProvider.refreshAll(applicationContext)
        }
    }

    private fun applyStateToTile(state: QuickBlockWidgetState) {
        val tile = qsTile ?: return
        tile.label = getString(R.string.tile_quick_block_label)
        tile.state = if (state.isActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE

        val subtitleText = when {
            state.isActive && state.isStrictLocked ->
                getString(R.string.widget_status_locked, state.remainingMinutes)
            state.isActive ->
                getString(R.string.widget_status_active, state.remainingMinutes)
            state.selectedAppsCount == 0 ->
                getString(R.string.widget_status_pick_apps)
            else ->
                getString(R.string.tile_quick_block_inactive_subtitle)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = subtitleText
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            tile.stateDescription = subtitleText
        }
        tile.contentDescription = "${getString(R.string.tile_quick_block_label)}: $subtitleText"
        tile.updateTile()
    }

    private fun openRebornApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                2001,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            runCatching { startActivityAndCollapse(pendingIntent) }
        } else {
            @Suppress("DEPRECATION")
            runCatching { startActivityAndCollapse(intent) }
        }
    }

    companion object {
        fun requestRefresh(context: Context) {
            runCatching {
                requestListeningState(
                    context.applicationContext,
                    ComponentName(context.applicationContext, RebornQuickBlockTileService::class.java),
                )
            }
        }
    }
}
