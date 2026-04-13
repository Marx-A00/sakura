package com.sakura.features.onboarding

import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sakura.preferences.AppPreferencesRepository
import com.sakura.preferences.StorageMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * UI states for the onboarding flow.
 * Progress: Welcome -> ModeSelection -> (LOCAL: Complete) or (SYNCTHING: CheckingPermission -> NeedsPermission/NeedsFolder -> Complete)
 */
sealed interface OnboardingUiState {
    /** Initial welcome screen — new user entry point. */
    data object Welcome : OnboardingUiState

    /** Mode selection: local-only vs Syncthing sync. */
    data object ModeSelection : OnboardingUiState

    /** Initial state while checking whether permission is already granted (Syncthing path). */
    data object CheckingPermission : OnboardingUiState

    /** MANAGE_EXTERNAL_STORAGE not granted — user must go to settings. */
    data object NeedsPermission : OnboardingUiState

    /** Permission granted but sync folder not yet configured. */
    data object NeedsFolder : OnboardingUiState

    /** Folder selected and onboarding saved — navigate to main. */
    data object Complete : OnboardingUiState
}

class OnboardingViewModel(
    private val prefsRepo: AppPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<OnboardingUiState>(OnboardingUiState.Welcome)
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val existingMode = prefsRepo.storageMode.first()
            if (existingMode == StorageMode.SYNCTHING) {
                // Re-onboarding for Syncthing setup (e.g., migrating from LOCAL via Settings).
                // Skip Welcome/ModeSelection and go straight to permission check.
                checkPermission()
            }
            // else: stay on Welcome (new user or LOCAL mode user — Welcome is correct start)
        }
    }

    /** Transitions from Welcome to ModeSelection. */
    fun onGetStarted() {
        _uiState.value = OnboardingUiState.ModeSelection
    }

    /**
     * Handles mode selection from ModeSelectionContent.
     * - LOCAL: saves mode, completes onboarding, transitions to Complete.
     * - SYNCTHING: saves mode, transitions to permission check flow.
     */
    fun onModeSelected(mode: StorageMode) {
        viewModelScope.launch {
            prefsRepo.setStorageMode(mode)
            when (mode) {
                StorageMode.LOCAL -> {
                    prefsRepo.setOnboardingComplete()
                    _uiState.value = OnboardingUiState.Complete
                }
                StorageMode.SYNCTHING -> {
                    checkPermission()
                }
            }
        }
    }

    /**
     * Re-evaluate whether MANAGE_EXTERNAL_STORAGE is granted.
     * Called in the Syncthing path and on every ON_RESUME lifecycle event so the
     * UI updates after the user returns from the system settings screen.
     */
    fun checkPermission() {
        _uiState.value = if (Environment.isExternalStorageManager()) {
            OnboardingUiState.NeedsFolder
        } else {
            OnboardingUiState.NeedsPermission
        }
    }

    /**
     * Called when the user confirms a sync folder path.
     * Persists the path and marks onboarding complete.
     */
    fun onFolderSelected(path: String) {
        viewModelScope.launch {
            prefsRepo.setSyncFolderPath(path)
            prefsRepo.setOnboardingComplete()
            _uiState.value = OnboardingUiState.Complete
        }
    }
}
