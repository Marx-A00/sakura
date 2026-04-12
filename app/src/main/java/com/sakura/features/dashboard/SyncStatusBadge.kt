package com.sakura.features.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sakura.sync.SyncStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Amber/warning color for conflict badge. */
private val WarningAmber = Color(0xFFF59E0B)

/** Orange for offline state. */
private val OfflineOrange = Color(0xFFEA580C)

/** Green for synced state. */
private val SyncedGreen = Color(0xFF3B5D44)

/**
 * Pill badge showing Syncthing sync status.
 *
 * - Synced: green pill with checkmark + "Synced" text (tappable to show snackbar with timestamp)
 * - Conflict: amber pill with warning icon + "Conflict" text
 * - Offline: orange pill when folder not accessible
 *
 * @param syncStatus  Current sync status from SyncBackend.checkSyncStatus()
 * @param onTap       Called when user taps the badge; passes a human-readable message to show
 */
@Composable
fun SyncStatusBadge(
    syncStatus: SyncStatus,
    onTap: (String) -> Unit = {}
) {
    val (pillColor, iconVector, labelText) = when {
        !syncStatus.folderAccessible -> Triple(OfflineOrange, Icons.Filled.Warning, "Offline")
        syncStatus.hasConflicts -> Triple(WarningAmber, Icons.Filled.Warning, "Conflict")
        else -> Triple(SyncedGreen, Icons.Filled.Check, "Synced")
    }

    val tapMessage = when {
        !syncStatus.folderAccessible -> "Sync folder not accessible. Check Settings."
        syncStatus.hasConflicts -> "Conflict detected — open Syncthing to resolve."
        syncStatus.lastSyncedAt != null -> {
            val formatted = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                .format(Date(syncStatus.lastSyncedAt))
            "Last synced: $formatted"
        }
        else -> "No sync activity yet."
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = pillColor,
        modifier = Modifier.clickable { onTap(tapMessage) }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = iconVector,
                contentDescription = labelText,
                modifier = Modifier.size(12.dp),
                tint = Color.White
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = labelText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
        }
    }
}
