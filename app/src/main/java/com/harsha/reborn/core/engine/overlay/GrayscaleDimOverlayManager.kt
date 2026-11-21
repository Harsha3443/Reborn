package com.harsha.reborn.core.engine.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Renders a non-touchable, desaturated grayscale-style dim overlay over apps that have
 * exceeded their daily limit when Grayscale Dim Mode is active.
 * Always passes touches/gestures straight through and removes itself on app exit.
 */
@Singleton
class GrayscaleDimOverlayManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var overlayView: View? = null
    private var badgeTextView: TextView? = null
    private var activePackage: String? = null

    fun updateOverlay(packageName: String?, shouldShow: Boolean, limitLabel: String = "") {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            applyOverlayState(packageName, shouldShow, limitLabel)
        } else {
            mainHandler.post { applyOverlayState(packageName, shouldShow, limitLabel) }
        }
    }

    fun hideOverlay() {
        updateOverlay(packageName = null, shouldShow = false)
    }

    private fun applyOverlayState(packageName: String?, shouldShow: Boolean, limitLabel: String) {
        if (!shouldShow || packageName.isNullOrBlank() || !Settings.canDrawOverlays(context)) {
            removeViewSafely()
            return
        }

        if (overlayView != null && activePackage == packageName) {
            badgeTextView?.text = buildBadgeText(limitLabel)
            return
        }

        removeViewSafely()

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val root = FrameLayout(context).apply {
            // Desaturated charcoal-slate veil that visibly dims vivid feeds while preserving readability
            setBackgroundColor(Color.argb(145, 28, 30, 34))
        }

        val badge = TextView(context).apply {
            text = buildBadgeText(limitLabel)
            setTextColor(Color.argb(225, 238, 240, 244))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setBackgroundColor(Color.argb(180, 18, 22, 28))
            setPadding(24, 10, 24, 10)
        }

        val badgeParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL,
        ).apply {
            topMargin = 96
        }
        root.addView(badge, badgeParams)

        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT,
        )

        runCatching {
            wm.addView(root, layoutParams)
            overlayView = root
            badgeTextView = badge
            activePackage = packageName
        }.onFailure {
            overlayView = null
            badgeTextView = null
            activePackage = null
        }
    }

    private fun removeViewSafely() {
        val current = overlayView ?: return
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        runCatching { wm?.removeViewImmediate(current) }
        overlayView = null
        badgeTextView = null
        activePackage = null
    }

    private fun buildBadgeText(limitLabel: String): String =
        if (limitLabel.isBlank()) {
            "Reborn • Daily limit reached (Grayscale dim)"
        } else {
            "Reborn • $limitLabel — Grayscale dim"
        }
}
