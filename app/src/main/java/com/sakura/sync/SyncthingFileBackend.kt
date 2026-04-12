package com.sakura.sync

import android.os.Environment
import android.util.AtomicFile
import com.sakura.preferences.AppPreferencesRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * SyncBackend implementation that reads/writes org files directly from a Syncthing-managed
 * folder on shared storage. Requires MANAGE_EXTERNAL_STORAGE permission.
 *
 * - Atomic writes via android.util.AtomicFile to prevent corruption on crash.
 * - Filters Syncthing conflict files (.sync-conflict) from listOrgFiles().
 * - Checks Environment.isExternalStorageManager() before every file operation.
 */
class SyncthingFileBackend(
    private val prefsRepo: AppPreferencesRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : SyncBackend {

    private fun checkPermission() {
        if (!Environment.isExternalStorageManager()) {
            throw SyncBackendError.PermissionDenied("All files access permission not granted")
        }
    }

    private suspend fun resolveFile(filename: String): File {
        val path = prefsRepo.syncFolderPath.first()
            ?: throw SyncBackendError.FolderUnavailable("Sync folder path is not configured")
        return File(path, filename)
    }

    override suspend fun readFile(filename: String): String = withContext(ioDispatcher) {
        checkPermission()
        try {
            val file = resolveFile(filename)
            if (!file.exists()) return@withContext ""
            file.readText(Charsets.UTF_8)
        } catch (e: SecurityException) {
            throw SyncBackendError.PermissionDenied("Security exception reading file: ${e.message}")
        }
    }

    override suspend fun writeFile(filename: String, content: String): Unit = withContext(ioDispatcher) {
        checkPermission()
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
        checkPermission()
        resolveFile(filename).exists()
    }

    override suspend fun listOrgFiles(): List<String> = withContext(ioDispatcher) {
        checkPermission()
        val path = prefsRepo.syncFolderPath.first() ?: return@withContext emptyList()
        val folder = File(path)
        if (!folder.exists() || !folder.isDirectory) return@withContext emptyList()
        folder.listFiles()
            ?.filter { file ->
                file.isFile &&
                    file.name.endsWith(".org") &&
                    !file.name.contains(".sync-conflict") &&
                    !file.name.startsWith(".")
            }
            ?.map { it.name }
            ?: emptyList()
    }

    override suspend fun checkSyncStatus(): SyncStatus = withContext(ioDispatcher) {
        val path = try {
            prefsRepo.syncFolderPath.first()
        } catch (e: Exception) {
            null
        } ?: return@withContext SyncStatus(null, false, false)

        val folder = File(path)
        if (!folder.exists() || !folder.isDirectory) {
            return@withContext SyncStatus(null, false, false)
        }

        // Check permission — if not granted, folder is not accessible
        if (!Environment.isExternalStorageManager()) {
            return@withContext SyncStatus(null, false, false)
        }

        val allFiles = folder.listFiles() ?: return@withContext SyncStatus(null, false, true)

        // Detect conflicts: any file with .sync-conflict in the name
        val hasConflicts = allFiles.any { it.isFile && it.name.contains(".sync-conflict") }

        // Last sync time: most recently modified non-conflict .org file
        val lastSyncedAt = allFiles
            .filter { it.isFile && it.name.endsWith(".org") && !it.name.contains(".sync-conflict") }
            .maxOfOrNull { it.lastModified() }

        SyncStatus(lastSyncedAt, hasConflicts, folderAccessible = true)
    }
}
