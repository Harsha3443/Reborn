package com.harsha.reborn.feature.about

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.harsha.reborn.core.data.local.SettingsRepository
import com.harsha.reborn.core.data.local.UserPreferences
import com.harsha.reborn.core.data.model.BackupRestoreResult
import com.harsha.reborn.core.data.model.ThemeMode
import com.harsha.reborn.core.data.repository.BackupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface BackupUiStatus {
    data object Idle : BackupUiStatus
    data object Working : BackupUiStatus
    data class Success(val message: String) : BackupUiStatus
    data class Error(val message: String) : BackupUiStatus
}

@HiltViewModel
class AboutViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val backupRepository: BackupRepository,
) : ViewModel() {

    val preferences: StateFlow<UserPreferences> = settingsRepository.preferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserPreferences(),
        )

    private val _backupStatus = MutableStateFlow<BackupUiStatus>(BackupUiStatus.Idle)
    val backupStatus: StateFlow<BackupUiStatus> = _backupStatus.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            settingsRepository.setThemeMode(mode)
        }
    }

    fun setDynamicColorEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setDynamicColorEnabled(enabled)
        }
    }

    fun exportBackupToUri(uri: Uri) {
        viewModelScope.launch {
            _backupStatus.value = BackupUiStatus.Working
            runCatching {
                val jsonText = backupRepository.exportBackupJson()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(jsonText.toByteArray(Charsets.UTF_8))
                        out.flush()
                    } ?: error("Unable to open output stream")
                }
            }.onSuccess {
                _backupStatus.value = BackupUiStatus.Success(
                    context.getString(R.string.backup_export_success)
                )
            }.onFailure { err ->
                _backupStatus.value = BackupUiStatus.Error(
                    context.getString(R.string.backup_error_prefix, err.message ?: "Write error")
                )
            }
        }
    }

    fun importBackupFromUri(uri: Uri) {
        viewModelScope.launch {
            _backupStatus.value = BackupUiStatus.Working
            val jsonText = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
                        it.readText()
                    } ?: error("Unable to read selected file")
                }
            }.getOrElse { err ->
                _backupStatus.value = BackupUiStatus.Error(
                    context.getString(R.string.backup_error_prefix, err.message ?: "Read error")
                )
                return@launch
            }

            when (val result = backupRepository.importBackupJson(jsonText)) {
                is BackupRestoreResult.Success -> {
                    _backupStatus.value = BackupUiStatus.Success(
                        context.getString(
                            R.string.backup_restore_success,
                            result.restoredRulesCount,
                            result.restoredAppsCount,
                        )
                    )
                }
                BackupRestoreResult.BlockedByStrictMode -> {
                    _backupStatus.value = BackupUiStatus.Error(
                        context.getString(R.string.backup_restore_strict_blocked)
                    )
                }
                is BackupRestoreResult.InvalidBackup -> {
                    _backupStatus.value = BackupUiStatus.Error(
                        context.getString(R.string.backup_error_prefix, result.reason)
                    )
                }
            }
        }
    }
}
