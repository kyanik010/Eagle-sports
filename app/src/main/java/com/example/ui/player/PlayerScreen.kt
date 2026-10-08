package com.example.ui.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.example.data.model.ChannelEntity
import com.example.player.AudioSourceMode
import com.example.player.PlaybackStatus
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
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@ExperimentalMaterial3Api
@Composable
fun PlayerScreen(
    channel: ChannelEntity,
    viewModel: PlayerViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val currentChannel by viewModel.currentChannel.collectAsState()
    val videoStatus by viewModel.videoStatus.collectAsState()
    val audioStatus by viewModel.audioStatus.collectAsState()
    val audioMode by viewModel.audioMode.collectAsState()
    val audioDelayMs by viewModel.audioDelayMs.collectAsState()
    val videoError by viewModel.videoError.collectAsState()
    val audioError by viewModel.audioError.collectAsState()
    val availableAudioTracks by viewModel.availableAudioTracks.collectAsState()

    var showControls by remember { mutableStateOf(true) }
    var showMoreSheet by remember { mutableStateOf(false) }
    var isFullscreen by remember { mutableStateOf(false) }

    LaunchedEffect(channel.stableId) {
        viewModel.loadChannel(channel)
    }

    // Auto-hide controls timer
    LaunchedEffect(showControls) {
        if (showControls) {
            delay(5000)
            showControls = false
        }
    }

    val activity = context as? Activity
    DisposableEffect(isFullscreen) {
        if (isFullscreen) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    val exitPlayer = {
        viewModel.stopPlayback()
        onBack()
    }

    BackHandler {
        if (isFullscreen) {
            isFullscreen = false
        } else {
            exitPlayer()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { showControls = !showControls }
            .testTag("player_screen")
    ) {
        // ExoPlayer Video View
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    useController = false
                    player = viewModel.dualPlayerController.getVideoPlayer()
                }
            },
            update = { playerView ->
                playerView.player = viewModel.dualPlayerController.getVideoPlayer()
            },
            modifier = Modifier.fillMaxSize()
        )

        // Buffering / Loading Indicator
        if (videoStatus == PlaybackStatus.BUFFERING || videoStatus == PlaybackStatus.PREPARING) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = GoldPrimary,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(52.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = if (videoStatus == PlaybackStatus.PREPARING) "Connecting Live Feed..." else "Buffering Stream...",
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                    )
                }
            }
        }

        // Reconnecting Notice
        if (videoStatus == PlaybackStatus.RECONNECTING) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = CyanAccent, modifier = Modifier.size(44.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Reconnecting to live broadcast...",
                        color = CyanAccent,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // Video Error Card (with retry button)
        if (videoStatus == PlaybackStatus.ERROR) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.8f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = NavySurface,
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, RedLive.copy(alpha = 0.5f)),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Stream Connection Error",
                            color = RedLive,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = videoError ?: "Unable to establish live stream connection.",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        IconButton(
                            onClick = { viewModel.retryVideo() },
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(GoldPrimary)
                                .size(44.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Retry", tint = NavyDeep)
                        }
                    }
                }
            }
        }

        // External Audio Notification Pill (independent engine state)
        if (audioMode is AudioSourceMode.External) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 16.dp, end = 16.dp)
            ) {
                Surface(
                    color = NavyDark.copy(alpha = 0.88f),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyanAccent.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Headphones,
                            contentDescription = null,
                            tint = CyanAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (audioStatus == PlaybackStatus.ERROR) "External Audio Error (Video Playing)"
                            else "External Commentary: ${audioDelayMs}ms",
                            color = if (audioStatus == PlaybackStatus.ERROR) RedLive else CyanAccent,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }

        // Player Controls Overlay
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.75f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.85f)
                            )
                        )
                    )
            ) {
                // Top Control Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = exitPlayer,
                        modifier = Modifier.testTag("player_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(GreenLive)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "LIVE • ${currentChannel?.channelNumber ?: channel.channelNumber}",
                                color = GoldPrimary,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        Text(
                            text = currentChannel?.name ?: channel.name,
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1
                        )
                    }

                    // Channel Favorite Toggle
                    IconButton(
                        onClick = { viewModel.toggleFavorite() },
                        modifier = Modifier.testTag("player_favorite_button")
                    ) {
                        Icon(
                            imageVector = if (currentChannel?.isFavorite == true) Icons.Default.Star else Icons.Outlined.StarOutline,
                            contentDescription = "Favorite",
                            tint = if (currentChannel?.isFavorite == true) GoldPrimary else Color.White
                        )
                    }

                    // Channel Up / Down
                    IconButton(onClick = { viewModel.channelDown() }) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Prev Channel", tint = Color.White)
                    }
                    IconButton(onClick = { viewModel.channelUp() }) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Next Channel", tint = Color.White)
                    }
                }

                // Center Play/Pause button
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = { viewModel.togglePlayPause() },
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(NavyDark.copy(alpha = 0.75f))
                            .border(1.5.dp, GoldPrimary, CircleShape)
                            .testTag("play_pause_button")
                    ) {
                        Icon(
                            imageVector = if (videoStatus == PlaybackStatus.PLAYING) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = GoldPrimary,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }

                // Bottom Control Bar: Back, Play/Pause, Volume, External Audio, More, Fullscreen
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // EPG Preview info
                    Column(modifier = Modifier.weight(1f)) {
                        currentChannel?.epgCurrentTitle?.let { epg ->
                            Text(
                                text = "NOW: $epg",
                                color = TextPrimary,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                maxLines = 1
                            )
                        }
                        currentChannel?.epgNextTitle?.let { nextEpg ->
                            Text(
                                text = "NEXT: $nextEpg",
                                color = TextMuted,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // External Audio Button
                        IconButton(
                            onClick = { showMoreSheet = true },
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (audioMode is AudioSourceMode.External) CyanAccent.copy(alpha = 0.25f) else Color.Transparent)
                                .testTag("external_audio_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Headphones,
                                contentDescription = "External Audio",
                                tint = if (audioMode is AudioSourceMode.External) CyanAccent else Color.White
                            )
                        }

                        // More Button
                        IconButton(
                            onClick = { showMoreSheet = true },
                            modifier = Modifier.testTag("more_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More",
                                tint = Color.White
                            )
                        }

                        // Fullscreen Toggle
                        IconButton(
                            onClick = { isFullscreen = !isFullscreen },
                            modifier = Modifier.testTag("fullscreen_button")
                        ) {
                            Icon(
                                imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                contentDescription = "Fullscreen",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }

        // More & Audio Options BottomSheet
        if (showMoreSheet) {
            ModalBottomSheet(
                onDismissRequest = { showMoreSheet = false },
                sheetState = rememberModalBottomSheetState(),
                containerColor = NavyDark,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    Text(
                        text = "Audio & Commentary Source",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = GoldPrimary
                        )
                    )
                    Text(
                        text = "Independent dual audio player engine without video interruption",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Option 1: Original Audio
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                viewModel.selectOriginalAudio()
                                showMoreSheet = false
                            },
                        color = if (audioMode is AudioSourceMode.Original) NavySurfaceVariant() else NavySurface
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Audiotrack, contentDescription = null, tint = GoldPrimary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Original Broadcast Audio", color = TextPrimary, fontWeight = FontWeight.Bold)
                                Text("Direct stream audio feed", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                            }
                            if (audioMode is AudioSourceMode.Original) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = GoldPrimary)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // External Audio Tracks
                    Text(
                        text = "External Sports Commentary (${availableAudioTracks.size} available)",
                        style = MaterialTheme.typography.labelMedium.copy(color = CyanAccent)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (availableAudioTracks.isEmpty()) {
                        Text(
                            text = "No matched external audio feeds found for this channel.",
                            color = TextMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.height(160.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(availableAudioTracks) { track ->
                                val isSelected = (audioMode as? AudioSourceMode.External)?.audioId == track.stableId
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            viewModel.selectExternalAudio(track)
                                            showMoreSheet = false
                                        },
                                    color = if (isSelected) NavySurfaceVariant() else NavySurface
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Headphones, contentDescription = null, tint = CyanAccent)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(track.title, color = TextPrimary, fontWeight = FontWeight.Medium)
                                            Text(
                                                "${track.commentator ?: "Commentator"} • ${track.bitrate ?: "320 kbps"}",
                                                color = TextSecondary,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                        if (isSelected) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = CyanAccent)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Audio Delay Controls (only active when External Audio is selected)
                    if (audioMode is AudioSourceMode.External) {
                        Spacer(modifier = Modifier.height(18.dp))
                        Text(
                            text = "Manual Audio Delay: ${audioDelayMs}ms (-3000ms to +3000ms)",
                            style = MaterialTheme.typography.labelLarge.copy(color = GoldPrimary, fontWeight = FontWeight.Bold)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(
                                onClick = { viewModel.adjustAudioDelay(-100) },
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(NavySurface)
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "-100ms", tint = GoldPrimary)
                            }
                            Text("-100ms", color = TextSecondary, style = MaterialTheme.typography.bodySmall)

                            IconButton(
                                onClick = { viewModel.adjustAudioDelay(-50) },
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(NavySurface)
                            ) {
                                Text("-50", color = CyanAccent, fontWeight = FontWeight.Bold)
                            }

                            Text(
                                "${audioDelayMs}ms",
                                color = GoldPrimary,
                                fontWeight = FontWeight.ExtraBold,
                                style = MaterialTheme.typography.titleMedium
                            )

                            IconButton(
                                onClick = { viewModel.adjustAudioDelay(50) },
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(NavySurface)
                            ) {
                                Text("+50", color = CyanAccent, fontWeight = FontWeight.Bold)
                            }

                            IconButton(
                                onClick = { viewModel.adjustAudioDelay(100) },
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(NavySurface)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "+100ms", tint = GoldPrimary)
                            }
                            Text("+100ms", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun NavySurfaceVariant(): Color = NavySurface.copy(alpha = 0.95f)
