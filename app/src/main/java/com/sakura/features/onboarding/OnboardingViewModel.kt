package com.sakura.features.onboarding

import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sakura.preferences.AppPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI states for the onboarding flow.
 * Progress: CheckingPermission -> NeedsPermission or NeedsFolder -> Complete
 */
sealed interface OnboardingUiState {
    /** Initial state while checking whether permission is already granted. */
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

    private val _uiState = MutableStateFlow<OnboardingUiState>(OnboardingUiState.CheckingPermission)
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        checkPermission()
    }

    /**
     * Re-evaluate whether MANAGE_EXTERNAL_STORAGE is granted.
     * Called on init and again on every ON_RESUME lifecycle event so the
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
