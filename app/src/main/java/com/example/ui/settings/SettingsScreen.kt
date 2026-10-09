package com.example.ui.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.SyncState
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.GreenLive
import com.example.ui.theme.NavyBorder
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.NavySurface
import com.example.ui.theme.RedLive
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onLogout: () -> Unit
) {
    val session by viewModel.userSession.collectAsState()
    val lastSync by viewModel.lastSyncTime.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val hwDecoding by viewModel.hwDecoding.collectAsState()
    val autoReconnect by viewModel.autoReconnect.collectAsState()
    val keepScreenOn by viewModel.keepScreenOn.collectAsState()
    val favoritesFirst by viewModel.favoritesFirst.collectAsState()
    val showLogos by viewModel.showLogos.collectAsState()
    val showNumbers by viewModel.showNumbers.collectAsState()

    val formattedSyncTime = if (lastSync > 0) {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        sdf.format(Date(lastSync))
    } else {
        "Never"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyDeep)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .testTag("settings_screen")
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("settings_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = GoldPrimary
                )
            }
            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = GoldPrimary
                )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Section 1: Synchronization
            SettingsSectionCard(title = "Synchronization", icon = Icons.Default.Sync) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Last Sync", color = TextPrimary, fontWeight = FontWeight.Medium)
                        Text(formattedSyncTime, color = TextMuted, style = MaterialTheme.typography.bodySmall)
                    }
                    Button(
                        onClick = { viewModel.syncAll() },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = NavyDeep),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("sync_now_button")
                    ) {
                        if (syncState is SyncState.Syncing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = NavyDeep, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text("Sync Now", fontWeight = FontWeight.Bold)
                    }
                }

                if (syncState is SyncState.Syncing) {
                    Text(
                        text = (syncState as SyncState.Syncing).message,
                        color = CyanAccent,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                } else if (syncState is SyncState.Success) {
                    val s = syncState as SyncState.Success
                    Text(
                        text = "Updated: ${s.channelsCount} channels, ${s.audioCount} commentary feeds",
                        color = if (s.warning == null) GreenLive else RedLive,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    s.warning?.let { warning ->
                        Text(
                            text = "Commentary sync issue: $warning",
                            color = RedLive,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                } else if (syncState is SyncState.Error) {
                    Text(
                        text = "Sync error: ${(syncState as SyncState.Error).message}",
                        color = RedLive,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { viewModel.syncChannelsOnly() },
                        colors = ButtonDefaults.buttonColors(containerColor = NavySurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NavyBorder),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Sync Channels", color = TextPrimary, fontSize = 12.sp)
                    }
                    Button(
                        onClick = { viewModel.syncAudioOnly() },
                        colors = ButtonDefaults.buttonColors(containerColor = NavySurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NavyBorder),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Sync Commentary", color = TextPrimary, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { viewModel.clearCatalog() }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = RedLive, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Rebuild / Clear Local Catalog", color = RedLive, fontSize = 13.sp)
                }
            }

            // Section 2: Playback Engine
            SettingsSectionCard(title = "Playback Engine", icon = Icons.Default.PlayCircle) {
                SettingsSwitchRow(
                    title = "Hardware Decoding",
                    subtitle = "Low-latency hardware acceleration for HEVC/H.264",
                    checked = hwDecoding,
                    onCheckedChange = { viewModel.toggleHwDecoding(it) }
                )
                SettingsSwitchRow(
                    title = "Auto Reconnect",
                    subtitle = "Automatically recover broken live connections",
                    checked = autoReconnect,
                    onCheckedChange = { viewModel.toggleAutoReconnect(it) }
                )
                SettingsSwitchRow(
                    title = "Keep Screen Awake",
                    subtitle = "Prevent device sleep during stream playback",
                    checked = keepScreenOn,
                    onCheckedChange = { viewModel.toggleKeepScreenOn(it) }
                )
            }

            // Section 3: Channels Display
            SettingsSectionCard(title = "Channels Display", icon = Icons.Default.Tv) {
                SettingsSwitchRow(
                    title = "Favorites First",
                    subtitle = "Always list starred channels at the top",
                    checked = favoritesFirst,
                    onCheckedChange = { viewModel.toggleFavoritesFirst(it) }
                )
                SettingsSwitchRow(
                    title = "Show Logos",
                    subtitle = "Display official channel logos in listings",
                    checked = showLogos,
                    onCheckedChange = { viewModel.toggleShowLogos(it) }
                )
                SettingsSwitchRow(
                    title = "Show Channel Numbers",
                    subtitle = "Display numeric channel indexing",
                    checked = showNumbers,
                    onCheckedChange = { viewModel.toggleShowNumbers(it) }
                )
            }

            // Section 4: Account & Device Info
            SettingsSectionCard(title = "Account & Device", icon = Icons.Default.AccountCircle) {
                InfoRow(label = "Username", value = session?.username ?: "Guest")
                InfoRow(label = "Status", value = session?.status ?: "Active")
                InfoRow(label = "Expiry Date", value = session?.expiryDate ?: "Never")
                InfoRow(label = "Device", value = "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})")

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { viewModel.logout(onLogout) },
                    colors = ButtonDefaults.buttonColors(containerColor = RedLive.copy(alpha = 0.15f), contentColor = RedLive),
                    border = androidx.compose.foundation.BorderStroke(1.dp, RedLive.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .testTag("logout_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.ExitToApp, contentDescription = null, tint = RedLive)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Logout & Clear Sensitive Session", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
fun SettingsSectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, NavyBorder, RoundedCornerShape(18.dp)),
        color = NavyDark,
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = GoldPrimary
                    )
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontWeight = FontWeight.Medium)
            Text(subtitle, color = TextMuted, style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = NavyDeep,
                checkedTrackColor = GoldPrimary,
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = NavySurface
            )
        )
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        Text(value, color = TextPrimary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
    }
}
