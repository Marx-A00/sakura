package com.sakura.sync

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
}
