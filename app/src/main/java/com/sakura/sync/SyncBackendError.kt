package com.sakura.sync

/**
 * Errors thrown by SyncBackend implementations.
 * Callers should catch these and map to appropriate UI states.
 */
sealed class SyncBackendError(message: String) : Exception(message) {
    /** The configured sync folder path is null or the folder does not exist. */
    class FolderUnavailable(message: String) : SyncBackendError(message)

    /** The app does not have the required file access permission. */
    class PermissionDenied(message: String) : SyncBackendError(message)

    /** A Syncthing conflict file was detected for the given filename. */
    class ConflictDetected(val filename: String) : SyncBackendError("Conflict file detected: $filename")
}
