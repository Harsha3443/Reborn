package com.harsha.reborn.feature.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
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

class QuickBlockWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val pendingResult = goAsync()
        scope.launch {
            try {
                val controller = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    EngineEntryPoint::class.java,
                ).quickBlockToggleController()
                val state = controller.readCurrentState()
                updateWidgets(context, appWidgetManager, appWidgetIds, state)
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TOGGLE_QUICK_BLOCK) {
            val pendingResult = goAsync()
            scope.launch {
                try {
                    val controller = EntryPointAccessors.fromApplication(
                        context.applicationContext,
                        EngineEntryPoint::class.java,
                    ).quickBlockToggleController()

                    when (controller.toggleQuickBlock()) {
                        QuickBlockToggleOutcome.NoAppsSelected -> {
                            val launchIntent = Intent(context, MainActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            }
                            runCatching { context.startActivity(launchIntent) }
                        }
                        else -> Unit
                    }

                    val updatedState = controller.readCurrentState()
                    val manager = AppWidgetManager.getInstance(context)
                    val ids = manager.getAppWidgetIds(
                        ComponentName(context, QuickBlockWidgetProvider::class.java)
                    )
                    if (ids.isNotEmpty()) {
                        updateWidgets(context, manager, ids, updatedState)
                    }
                    RebornQuickBlockTileService.requestRefresh(context)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    companion object {
        private const val ACTION_TOGGLE_QUICK_BLOCK = "com.harsha.reborn.ACTION_TOGGLE_QUICK_BLOCK"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun refreshAll(context: Context) {
            scope.launch {
                runCatching {
                    val manager = AppWidgetManager.getInstance(context)
                    val ids = manager.getAppWidgetIds(
                        ComponentName(context, QuickBlockWidgetProvider::class.java)
                    )
                    if (ids.isEmpty()) return@runCatching
                    val controller = EntryPointAccessors.fromApplication(
                        context.applicationContext,
                        EngineEntryPoint::class.java,
                    ).quickBlockToggleController()
                    val state = controller.readCurrentState()
                    updateWidgets(context, manager, ids, state)
                }
            }
        }

        private fun updateWidgets(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray,
            state: QuickBlockWidgetState,
        ) {
            val toggleIntent = Intent(context, QuickBlockWidgetProvider::class.java).apply {
                action = ACTION_TOGGLE_QUICK_BLOCK
            }
            val togglePendingIntent = PendingIntent.getBroadcast(
                context,
                1001,
                toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val openAppPendingIntent = PendingIntent.getActivity(
                context,
                1002,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val statusText = when {
                state.isActive && state.isStrictLocked ->
                    context.getString(R.string.widget_status_locked, state.remainingMinutes)
                state.isActive ->
                    context.getString(R.string.widget_status_active, state.remainingMinutes)
                state.selectedAppsCount == 0 ->
                    context.getString(R.string.widget_status_pick_apps)
                else ->
                    context.getString(R.string.widget_status_off)
            }

            val buttonLabel = when {
                state.isActive && state.isStrictLocked ->
                    context.getString(R.string.widget_action_locked)
                state.isActive ->
                    context.getString(R.string.widget_action_stop)
                state.selectedAppsCount == 0 ->
                    context.getString(R.string.widget_action_pick)
                else ->
                    context.getString(R.string.widget_action_start)
            }

            appWidgetIds.forEach { appWidgetId ->
                val views = RemoteViews(context.packageName, R.layout.widget_quick_block).apply {
                    setTextViewText(R.id.widget_title, context.getString(R.string.widget_quick_block_title))
                    setTextViewText(R.id.widget_status, statusText)
                    setTextViewText(R.id.widget_toggle_button, buttonLabel)
                    setContentDescription(R.id.widget_toggle_button, "$buttonLabel — $statusText")
                    setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent)
                    setOnClickPendingIntent(R.id.widget_toggle_button, togglePendingIntent)
                }
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }
}
