package com.sakura.sync

/**
 * Status of the Syncthing folder: last sync time, conflict detection, and accessibility.
 * Used by DashboardViewModel for the SyncStatusBadge.
 */
data class SyncStatus(
    val lastSyncedAt: Long?,        // epoch millis of most recently modified .org file
    val hasConflicts: Boolean,      // true if any .sync-conflict file is present
    val folderAccessible: Boolean   // false if folder path not configured or permission denied
)

/**
 * Abstraction over the underlying file sync mechanism.
 * v1 implementation: SyncthingFileBackend (direct file I/O on shared storage).
 * Future: could be replaced with a network-based or SAF-based backend.
 */
interface SyncBackend {
    suspend fun readFile(filename: String): String
    suspend fun writeFile(filename: String, content: String)
    suspend fun fileExists(filename: String): Boolean
    suspend fun listOrgFiles(): List<String>

    /**
     * Check sync folder status for conflict detection and last-synced timestamp.
     * Does NOT filter out .sync-conflict files — intentionally checks for them.
     */
    suspend fun checkSyncStatus(): SyncStatus
}
