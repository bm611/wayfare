package com.wayfare.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The third tab. Everything that is about the account rather than about a trip
 * used to live in an overflow menu on the trips list; this gives it somewhere to
 * be, which is what the design's third tab is for.
 */
@Composable
fun AccountScreen(
    displayName: String?,
    email: String?,
    onJoin: suspend (String) -> Result<String>,
    onJoined: (String) -> Unit,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    var confirmSignOut by remember { mutableStateOf(false) }
    var showJoin by remember { mutableStateOf(false) }

    Scaffold(containerColor = Night, bottomBar = bottomBar) { insets ->
        Column(
            Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp).padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) { Brand(Modifier.weight(1f)) }

            Text(
                "You",
                Modifier.padding(start = 8.dp, top = 14.dp),
                style = MaterialTheme.typography.displaySmall,
            )

            Row(
                Modifier.fillMaxWidth().padding(top = 22.dp)
                    .clip(RoundedCornerShape(28.dp)).background(CardNavy).padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(Amber),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        displayName?.trim()?.firstOrNull()?.uppercase() ?: "W",
                        color = Night,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(
                        displayName ?: "Traveller",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    email?.let {
                        Text(
                            it,
                            Modifier.padding(top = 2.dp),
                            color = Slate,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            MonoLabel("ACTIONS", Modifier.padding(start = 8.dp, top = 26.dp, bottom = 10.dp), color = Slate, tracking = 2.sp)
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(CardNavy)) {
                AccountRow(
                    Icons.Outlined.ConfirmationNumber,
                    "Join a trip",
                    "Redeem an eight-character invite code",
                    onClick = { showJoin = true },
                )
                HairlineDivider(Modifier.padding(horizontal = 20.dp))
                AccountRow(
                    Icons.Outlined.Refresh,
                    "Refresh everything",
                    "Pull the latest trips and lines from the server",
                    onClick = onRefresh,
                )
                HairlineDivider(Modifier.padding(horizontal = 20.dp))
                AccountRow(
                    Icons.AutoMirrored.Outlined.Logout,
                    "Sign out",
                    "Clears cached trips and any unsynced lines from this phone",
                    onClick = { confirmSignOut = true },
                    tint = ErrorRed,
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (showJoin) JoinTripDialog(
        initialCode = "",
        onDismiss = { showJoin = false },
        onJoin = { code -> onJoin(code).onSuccess { showJoin = false; onJoined(it) } },
    )
    if (confirmSignOut) AlertDialog(
        onDismissRequest = { confirmSignOut = false },
        shape = RoundedCornerShape(28.dp),
        containerColor = CardNavy,
        title = { Text("Sign out?", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Text(
                "Cached trips and any lines still waiting to sync will be removed from this phone. " +
                    "Sync pending lines first to keep them.",
                color = Slate,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = { confirmSignOut = false; onSignOut() }) {
                Text("Sign out", color = ErrorRed, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Stay signed in", color = Steel) } },
    )
}

@Composable
private fun AccountRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color = Steel,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = tint)
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(title, color = if (tint == ErrorRed) ErrorRed else Paper, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, Modifier.padding(top = 2.dp), color = Slate, style = MaterialTheme.typography.bodySmall)
        }
    }
}
