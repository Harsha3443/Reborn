package com.harsha.reborn.feature.smart

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harsha.reborn.core.data.local.SettingsRepository
import com.harsha.reborn.core.data.model.SmartRulesConfig
import com.harsha.reborn.core.data.repository.BlockingRulesRepository
import com.harsha.reborn.core.data.repository.StrictModeRepository
import com.harsha.reborn.core.engine.PermissionHealthManager
import com.harsha.reborn.core.engine.RebornPermissionType
import com.harsha.reborn.core.engine.feed.FeedBlocker
import com.harsha.reborn.core.engine.feed.FeedSelectorCatalog
import com.harsha.reborn.core.engine.feed.FeedSelectorRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class FeedAppToggleUi(
    val profileId: String,
    val title: String,
    val packageName: String,
    val description: String,
    val isEnabled: Boolean,
)

data class FeedBlockingUiState(
    val appToggles: List<FeedAppToggleUi> = emptyList(),
    val allowReelsFromDms: Boolean = false,
    val isStrictLocked: Boolean = false,
    val strictRemainingMinutes: Int = 0,
    val isAccessibilityEnabled: Boolean = true,
    val usingCustomJson: Boolean = false,
    val customFeedSelectorsJson: String = "",
    val activeCatalog: FeedSelectorCatalog = FeedSelectorCatalog(),
    val feedbackMessage: String? = null,
)

@HiltViewModel
class FeedBlockingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val blockingRulesRepository: BlockingRulesRepository,
    private val strictModeRepository: StrictModeRepository,
    private val permissionHealthManager: PermissionHealthManager,
    private val feedBlocker: FeedBlocker,
) : ViewModel() {

    private val feedbackFlow = MutableStateFlow<String?>(null)

    private val tickerFlow = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(5_000L)
        }
    }

    val uiState: StateFlow<FeedBlockingUiState> = combine(
        settingsRepository.preferencesFlow,
        strictModeRepository.strictConfigFlow,
        permissionHealthManager.healthSnapshot,
        feedbackFlow,
        tickerFlow,
    ) { prefs, strictConfig, health, feedback, nowMs ->
        val smart = prefs.smartRulesConfig
        val catalog = feedBlocker.loadCatalog(smart.customFeedSelectorsJson)
        val isStrictLocked = strictConfig.isEffectivelyLocked(nowMs)
        val strictRemainingMin = if (isStrictLocked) {
            ((strictConfig.endsAtEpochMs - nowMs + 59_999L) / 60_000L).toInt().coerceAtLeast(1)
        } else {
            0
        }
        val isA11yEnabled = health.items.any {
            it.type == RebornPermissionType.ACCESSIBILITY && it.isGranted
        }

        val toggles = buildAppToggles(smart = smart, catalog = catalog)

        FeedBlockingUiState(
            appToggles = toggles,
            allowReelsFromDms = smart.allowReelsFromDms,
            isStrictLocked = isStrictLocked,
            strictRemainingMinutes = strictRemainingMin,
            isAccessibilityEnabled = isA11yEnabled,
            usingCustomJson = smart.customFeedSelectorsJson.isNotBlank(),
            customFeedSelectorsJson = smart.customFeedSelectorsJson,
            activeCatalog = catalog,
            feedbackMessage = feedback,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FeedBlockingUiState(),
    )

    fun refreshHealth() {
        permissionHealthManager.refresh()
    }

    fun clearFeedback() {
        feedbackFlow.value = null
    }

    fun setAppFeedEnabled(profileId: String, enabled: Boolean) {
        viewModelScope.launch {
            if (isLockedByStrict()) return@launch
            val state = uiState.value
            val currentConfiguredIds = state.appToggles
                .filter { it.isEnabled }
                .map { it.profileId }
                .toMutableSet()

            if (enabled) {
                currentConfiguredIds.add(profileId)
            } else {
                currentConfiguredIds.remove(profileId)
            }

            val anyEnabled = currentConfiguredIds.isNotEmpty()
            blockingRulesRepository.updateFeedBlockingState(
                enabled = anyEnabled,
                enabledProfileIds = currentConfiguredIds,
            )
        }
    }

    fun setAllowReelsFromDms(enabled: Boolean) {
        viewModelScope.launch {
            if (isLockedByStrict()) return@launch
            blockingRulesRepository.setAllowReelsFromDms(enabled)
        }
    }

    fun saveCustomSelectorsJson(rawJson: String): Boolean {
        if (isLockedByStrict()) return false
        val trimmed = rawJson.trim()
        if (trimmed.isEmpty()) {
            viewModelScope.launch {
                blockingRulesRepository.setCustomFeedSelectorsJson("")
                feedbackFlow.value = "Restored built-in feed_selectors.json config."
            }
            return true
        }
        val validation = FeedSelectorRegistry.validateCatalogJson(trimmed)
        if (validation.isFailure) {
            feedbackFlow.value =
                "Invalid selector JSON: ${validation.exceptionOrNull()?.message ?: "check format"}"
            return false
        }
        viewModelScope.launch {
            blockingRulesRepository.setCustomFeedSelectorsJson(trimmed)
            feedbackFlow.value =
                "Loaded ${validation.getOrNull()?.profiles?.size ?: 0} feed selector profiles."
        }
        return true
    }

    fun importSelectorsFromUri(uri: Uri) {
        viewModelScope.launch {
            if (isLockedByStrict()) return@launch
            val rawResult = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: error("Could not read selected file.")
                }
            }
            val rawJson = rawResult.getOrElse { err ->
                feedbackFlow.value = "Import failed: ${err.message ?: "unable to read file"}"
                return@launch
            }
            val validation = FeedSelectorRegistry.validateCatalogJson(rawJson)
            if (validation.isFailure) {
                feedbackFlow.value =
                    "Invalid selector JSON: ${validation.exceptionOrNull()?.message ?: "check format"}"
                return@launch
            }
            blockingRulesRepository.setCustomFeedSelectorsJson(rawJson.trim())
            val count = validation.getOrNull()?.profiles?.size ?: 0
            feedbackFlow.value = "Imported $count feed selector profile${if (count == 1) "" else "s"}."
        }
    }

    fun resetToDefaultJson() {
        viewModelScope.launch {
            if (isLockedByStrict()) return@launch
            blockingRulesRepository.setCustomFeedSelectorsJson("")
            feedbackFlow.value = "Restored default feed_selectors.json asset."
        }
    }

    private fun isLockedByStrict(): Boolean {
        if (uiState.value.isStrictLocked) {
            feedbackFlow.value = "Feed Blocking toggles are locked while Strict Mode is active."
            return true
        }
        return false
    }

    private fun buildAppToggles(
        smart: SmartRulesConfig,
        catalog: FeedSelectorCatalog,
    ): List<FeedAppToggleUi> {
        val coreDefinitions = listOf(
            Quad("youtube_shorts", "YouTube Shorts", "com.google.android.youtube", "Blocks vertical Shorts feed while keeping search & long videos."),
            Quad("instagram_reels", "Instagram Reels", "com.instagram.android", "Exits the Reels viewer and tab while preserving Feed & DMs."),
            Quad("facebook_reels", "Facebook Reels", "com.facebook.katana", "Blocks Facebook Reels while keeping groups, events, and chats."),
            Quad("snapchat_spotlight", "Snapchat Spotlight", "com.snapchat.android", "Blocks Spotlight short videos while keeping Snaps & Chat."),
        )

        val coreItems = coreDefinitions.map { def ->
            val fromCatalog = catalog.profiles.firstOrNull { it.id == def.id }
            FeedAppToggleUi(
                profileId = def.id,
                title = fromCatalog?.displayName ?: def.title,
                packageName = fromCatalog?.packageNames?.firstOrNull() ?: def.pkg,
                description = def.desc,
                isEnabled = smart.inAppFeedBlockingEnabled && def.id in smart.enabledFeedProfileIds,
            )
        }

        val coreIds = coreDefinitions.map { it.id }.toSet()
        val extraCustomItems = if (smart.customFeedSelectorsJson.isNotBlank()) {
            catalog.profiles
                .filter { it.id !in coreIds && it.id != "tiktok_feed" }
                .map { profile ->
                    FeedAppToggleUi(
                        profileId = profile.id,
                        title = profile.displayName,
                        packageName = profile.packageNames.firstOrNull().orEmpty(),
                        description = "Custom imported feed profile",
                        isEnabled = smart.inAppFeedBlockingEnabled && profile.id in smart.enabledFeedProfileIds,
                    )
                }
        } else {
            emptyList()
        }

        return coreItems + extraCustomItems
    }

    private data class Quad(
        val id: String,
        val title: String,
        val pkg: String,
        val desc: String,
    )
}
