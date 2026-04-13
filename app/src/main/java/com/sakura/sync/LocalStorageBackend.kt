package com.sakura.sync

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * SyncBackend implementation that reads/writes org files directly to internal storage
 * (Context.filesDir). No permissions required — internal storage is always accessible
 * to the app without any runtime permission checks.
 *
 * Designed for non-technical users (e.g., mom on Android) who don't use Syncthing.
 * Files live in the app's private directory and are not synced to any external service.
 *
 * - Atomic writes via android.util.AtomicFile to prevent corruption on crash.
 * - Filters dotfiles from listOrgFiles().
 * - checkSyncStatus() always returns folderAccessible = true — no sync concept.
 */
class LocalStorageBackend(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : SyncBackend {

    private fun resolveFile(filename: String): File = File(context.filesDir, filename)

    override suspend fun readFile(filename: String): String = withContext(ioDispatcher) {
        val file = resolveFile(filename)
        if (!file.exists()) return@withContext ""
        file.readText(Charsets.UTF_8)
    }

    override suspend fun writeFile(filename: String, content: String): Unit = withContext(ioDispatcher) {
        val file = resolveFile(filename)
        val atomicFile = AtomicFile(file)
        var stream = atomicFile.startWrite()
        try {
            stream.write(content.toByteArray(Charsets.UTF_8))
            atomicFile.finishWrite(stream)
            stream = null
        } catch (e: IOException) {
            if (stream != null) {
                atomicFile.failWrite(stream)
            }
            throw e
        }
    }

    override suspend fun fileExists(filename: String): Boolean = withContext(ioDispatcher) {
        resolveFile(filename).exists()
    }

    override suspend fun listOrgFiles(): List<String> = withContext(ioDispatcher) {
        val dir = context.filesDir
        if (!dir.exists() || !dir.isDirectory) return@withContext emptyList()
        dir.listFiles()
            ?.filter { file ->
                file.isFile &&
                    file.name.endsWith(".org") &&
                    !file.name.startsWith(".")
            }
            ?.map { it.name }
            ?: emptyList()
    }

    /**
     * Local storage is always accessible — no sync concept, no conflicts.
     * lastSyncedAt is null because there is no remote sync to report.
     */
    override suspend fun checkSyncStatus(): SyncStatus = SyncStatus(
        lastSyncedAt = null,
        hasConflicts = false,
        folderAccessible = true
    )
}
