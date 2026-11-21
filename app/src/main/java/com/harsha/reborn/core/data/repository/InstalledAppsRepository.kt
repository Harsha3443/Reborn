package com.harsha.reborn.core.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.harsha.reborn.core.data.model.AppCategory
import com.harsha.reborn.core.data.model.InstalledAppInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Discovers launchable apps on the device using targeted <queries> intent filters
 * (Play-policy compliant without QUERY_ALL_PACKAGES) and caches rendered app icons.
 */
@Singleton
class InstalledAppsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val packageManager: PackageManager = context.packageManager

    // Cache decoded 96x96 ImageBitmaps in memory to keep LazyColumn scrolling smooth.
    private val iconCache = LruCache<String, ImageBitmap>(180)

    suspend fun getLaunchableApps(): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        val protectedPackages = resolveProtectedPackages()

        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val activities = queryActivitiesCompat(launcherIntent)

        activities
            .asSequence()
            .mapNotNull { resolveInfo ->
                val appInfo = resolveInfo.activityInfo?.applicationInfo ?: return@mapNotNull null
                val packageName = appInfo.packageName ?: return@mapNotNull null
                val label = resolveInfo.loadLabel(packageManager).toString().trim()
                    .takeIf { it.isNotEmpty() } ?: packageName

                val protectionReason = protectedPackages[packageName]
                InstalledAppInfo(
                    packageName = packageName,
                    label = label,
                    category = resolveCategory(appInfo, packageName),
                    isProtectedSystemApp = protectionReason != null,
                    protectionReason = protectionReason,
                )
            }
            .distinctBy { it.packageName }
            .sortedWith(
                compareBy<InstalledAppInfo> { it.isProtectedSystemApp }
                    .thenBy { it.label.lowercase() }
            )
            .toList()
    }

    suspend fun loadAppIcon(packageName: String): ImageBitmap? = withContext(Dispatchers.IO) {
        iconCache.get(packageName)?.let { return@withContext it }

        val drawable = runCatching { packageManager.getApplicationIcon(packageName) }.getOrNull()
            ?: return@withContext null

        val bitmap = drawableToBitmap(drawable, sizePx = 96).asImageBitmap()
        iconCache.put(packageName, bitmap)
        bitmap
    }

    /**
     * Always protect self, dialers, emergency callers, and home launchers so the user
     * can never accidentally lock themselves out of calling or reaching the home screen.
     */
    private fun resolveProtectedPackages(): Map<String, String> {
        val protected = mutableMapOf<String, String>()
        protected[context.packageName] = "Reborn app"

        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        queryActivitiesCompat(homeIntent).forEach { info ->
            info.activityInfo?.packageName?.let { pkg ->
                protected.putIfAbsent(pkg, "Home launcher")
            }
        }

        val dialIntent = Intent(Intent.ACTION_DIAL)
        queryActivitiesCompat(dialIntent).forEach { info ->
            info.activityInfo?.packageName?.let { pkg ->
                protected.putIfAbsent(pkg, "Phone & emergency")
            }
        }

        listOf(
            "com.android.phone",
            "com.android.server.telecom",
            "com.android.emergency",
            "com.google.android.dialer",
            "com.samsung.android.dialer",
        ).forEach { pkg ->
            protected.putIfAbsent(pkg, "Phone & emergency")
        }

        return protected
    }

    @Suppress("DEPRECATION")
    private fun queryActivitiesCompat(intent: Intent): List<ResolveInfo> {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.queryIntentActivities(
                    intent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()),
                )
            } else {
                packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            }
        }.getOrElse { emptyList() }
    }

    private fun resolveCategory(appInfo: ApplicationInfo, packageName: String): AppCategory {
        val androidCategory = appInfo.category
        when (androidCategory) {
            ApplicationInfo.CATEGORY_SOCIAL -> return AppCategory.SOCIAL
            ApplicationInfo.CATEGORY_VIDEO,
            ApplicationInfo.CATEGORY_AUDIO -> return AppCategory.VIDEO_AUDIO
            ApplicationInfo.CATEGORY_GAME -> return AppCategory.GAMES
            ApplicationInfo.CATEGORY_NEWS -> return AppCategory.NEWS_READING
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> return AppCategory.PRODUCTIVITY
        }

        val pkg = packageName.lowercase()
        return when {
            pkg.contains("instagram") || pkg.contains("twitter") || pkg.contains("facebook") ||
                pkg.contains("snapchat") || pkg.contains("reddit") || pkg.contains("threads") ||
                pkg.contains("whatsapp") || pkg.contains("telegram") || pkg.contains("discord") ||
                pkg.contains("musically") || pkg.contains("tiktok") -> AppCategory.SOCIAL

            pkg.contains("youtube") || pkg.contains("netflix") || pkg.contains("spotify") ||
                pkg.contains("twitch") || pkg.contains("primevideo") || pkg.contains("hotstar") ->
                AppCategory.VIDEO_AUDIO

            pkg.contains("game") || pkg.contains("supercell") || pkg.contains("roblox") ||
                pkg.contains("minecraft") -> AppCategory.GAMES

            pkg.contains("amazon") || pkg.contains("flipkart") || pkg.contains("ebay") ||
                pkg.contains("shopify") || pkg.contains("myntra") -> AppCategory.SHOPPING

            pkg.contains("kindle") || pkg.contains("news") || pkg.contains("medium") ||
                pkg.contains("substack") -> AppCategory.NEWS_READING

            pkg.contains("docs") || pkg.contains("sheets") || pkg.contains("notion") ||
                pkg.contains("slack") || pkg.contains("calendar") || pkg.contains("gm") ->
                AppCategory.PRODUCTIVITY

            else -> AppCategory.OTHER
        }
    }

    private fun drawableToBitmap(drawable: Drawable, sizePx: Int): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            val bmp = drawable.bitmap
            if (bmp.width == sizePx && bmp.height == sizePx) return bmp
            return Bitmap.createScaledBitmap(bmp, sizePx, sizePx, true)
        }
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
}
