package com.example.ui.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
    val isFavorite by viewModel.isFavorite.collectAsState()
    val videoStatus by viewModel.videoStatus.collectAsState()
    val audioStatus by viewModel.audioStatus.collectAsState()
    val audioMode by viewModel.audioMode.collectAsState()
    val audioDelayMs by viewModel.audioDelayMs.collectAsState()
    val videoError by viewModel.videoError.collectAsState()
    val audioError by viewModel.audioError.collectAsState()
    val availableAudioTracks by viewModel.availableAudioTracks.collectAsState()

    var showControls by remember { mutableStateOf(true) }
    var showMoreSheet by remember { mutableStateOf(false) }
    var showDelayControls by remember { mutableStateOf(false) }
    var isFullscreen by remember { mutableStateOf(false) }
    val controlsFocusRequester = remember { FocusRequester() }
    val isTelevision = (context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION

    LaunchedEffect(channel.stableId) {
        viewModel.loadChannel(channel)
    }

    // Keep controls visible on Android TV so the remote never loses its target.
    // On touch devices, retain the existing five-second auto-hide behavior.
    LaunchedEffect(showControls, isTelevision, showMoreSheet) {
        if (showControls && !isTelevision && !showMoreSheet) {
            delay(5000)
            showControls = false
        }
    }

    // When controls are shown on TV, place initial focus on a real actionable control.
    LaunchedEffect(isTelevision, showControls) {
        if (isTelevision && showControls) {
            kotlinx.coroutines.yield()
            runCatching { controlsFocusRequester.requestFocus() }
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
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && !showControls &&
                    event.key in setOf(
                        Key.DirectionUp,
                        Key.DirectionDown,
                        Key.DirectionLeft,
                        Key.DirectionRight,
                        Key.DirectionCenter,
                        Key.Enter,
                        Key.NumPadEnter
                    )
                ) {
                    showControls = true
                    true
                } else {
                    false
                }
            }
            .clickable { if (!isTelevision) showControls = !showControls else showControls = true }
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
                        text = if (videoStatus == PlaybackStatus.PREPARING) "جارٍ الاتصال بالبث المباشر..." else "جارٍ تحميل البث...",
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
                        text = "جارٍ إعادة الاتصال بالبث المباشر...",
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
                            text = "خطأ في الاتصال بالبث",
                            color = RedLive,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = videoError ?: "تعذّر إنشاء اتصال بالبث المباشر.",
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
                            Icon(Icons.Default.Refresh, contentDescription = "إعادة المحاولة", tint = NavyDeep)
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
                            text = if (audioStatus == PlaybackStatus.ERROR) "خطأ في الصوت الخارجي (الفيديو يعمل)"
                            else "التعليق الخارجي: ${audioDelayMs} مللي ثانية",
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
                            contentDescription = "رجوع",
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
                                text = "مباشر • ${currentChannel?.channelNumber ?: channel.channelNumber}",
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
                            imageVector = if (isFavorite) Icons.Default.Star else Icons.Outlined.StarOutline,
                            contentDescription = "المفضلة",
                            tint = if (isFavorite) GoldPrimary else Color.White
                        )
                    }

                    // Channel Up / Down
                    IconButton(onClick = { viewModel.channelDown() }) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "القناة السابقة", tint = Color.White)
                    }
                    IconButton(onClick = { viewModel.channelUp() }) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "القناة التالية", tint = Color.White)
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
                            .focusRequester(controlsFocusRequester)
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(NavyDark.copy(alpha = 0.75f))
                            .border(1.5.dp, GoldPrimary, CircleShape)
                            .testTag("play_pause_button")
                    ) {
                        Icon(
                            imageVector = if (videoStatus == PlaybackStatus.PLAYING) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "تشغيل / إيقاف مؤقت",
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
                                text = "الآن: $epg",
                                color = TextPrimary,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                maxLines = 1
                            )
                        }
                        currentChannel?.epgNextTitle?.let { nextEpg ->
                            Text(
                                text = "التالي: $nextEpg",
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
                                contentDescription = "الصوت الخارجي",
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
                                contentDescription = "المزيد",
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
                                contentDescription = "ملء الشاشة",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }

        // Compact audio source sheet: keep channel choices prominent and delay tools collapsed.
        if (showMoreSheet) {
            ModalBottomSheet(
                onDismissRequest = { showMoreSheet = false },
                sheetState = rememberModalBottomSheetState(),
                containerColor = NavyDark,
                shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "مصدر الصوت والتعليق",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = GoldPrimary
                                )
                            )
                            Text(
                                text = "اختر صوت البث أو التعليق الخارجي",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                            )
                        }
                        Surface(
                            color = NavySurface,
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NavyBorder)
                        ) {
                            Text(
                                text = "${availableAudioTracks.size} مصدر",
                                color = CyanAccent,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                viewModel.selectOriginalAudio()
                                showMoreSheet = false
                            },
                        color = if (audioMode is AudioSourceMode.Original) NavySurfaceVariant() else NavySurface,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (audioMode is AudioSourceMode.Original) GoldPrimary.copy(alpha = 0.7f) else NavyBorder
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Audiotrack, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("صوت البث الأصلي", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("الصوت المضمّن في البث", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                            }
                            if (audioMode is AudioSourceMode.Original) {
                                Icon(Icons.Default.Check, contentDescription = "محدد", tint = GoldPrimary, modifier = Modifier.size(19.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "التعليق الرياضي الخارجي",
                        style = MaterialTheme.typography.labelLarge.copy(color = CyanAccent, fontWeight = FontWeight.SemiBold)
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    if (availableAudioTracks.isEmpty()) {
                        Surface(
                            color = NavySurface,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "لا توجد مصادر تعليق مطابقة لهذه القناة.",
                                color = TextMuted,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 132.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(availableAudioTracks) { track ->
                                val isSelected = (audioMode as? AudioSourceMode.External)?.audioId == track.stableId
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            viewModel.selectExternalAudio(track)
                                            showMoreSheet = false
                                        },
                                    color = if (isSelected) NavySurfaceVariant() else NavySurface,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isSelected) CyanAccent.copy(alpha = 0.65f) else NavyBorder
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Headphones, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(19.dp))
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(track.title, color = TextPrimary, fontWeight = FontWeight.Medium, fontSize = 13.sp, maxLines = 1)
                                            Text(
                                                "${track.commentator ?: "معلق خارجي"} • ${track.bitrate ?: "320 kbps"}",
                                                color = TextSecondary,
                                                style = MaterialTheme.typography.labelSmall,
                                                maxLines = 1
                                            )
                                        }
                                        if (isSelected) {
                                            Icon(Icons.Default.Check, contentDescription = "محدد", tint = CyanAccent, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (audioMode is AudioSourceMode.External) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showDelayControls = !showDelayControls },
                            color = NavySurface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, NavyBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("ضبط تأخير الصوت", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text("تعديل يدوي من ‎−3000 إلى ‎+3000 مللي ثانية", color = TextMuted, style = MaterialTheme.typography.labelSmall)
                                }
                                Surface(color = NavyDark, shape = RoundedCornerShape(8.dp)) {
                                    Text(
                                        "${audioDelayMs} ms",
                                        color = GoldPrimary,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelLarge,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = if (showDelayControls) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (showDelayControls) "إخفاء ضبط التأخير" else "إظهار ضبط التأخير",
                                    tint = GoldPrimary
                                )
                            }
                        }
                        if (showDelayControls) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                DelayStepButton("−100", enabled = audioDelayMs > -3000, onClick = { viewModel.adjustAudioDelay(-100) }, modifier = Modifier.weight(1f))
                                DelayStepButton("−50", enabled = audioDelayMs > -3000, onClick = { viewModel.adjustAudioDelay(-50) }, modifier = Modifier.weight(1f))
                                DelayStepButton("+50", enabled = audioDelayMs < 3000, onClick = { viewModel.adjustAudioDelay(50) }, modifier = Modifier.weight(1f))
                                DelayStepButton("+100", enabled = audioDelayMs < 3000, onClick = { viewModel.adjustAudioDelay(100) }, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}


@Composable
private fun DelayStepButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .clickable(enabled = enabled, onClick = onClick),
        color = if (enabled) NavySurface else NavyDark,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (enabled) NavyBorder else NavyBorder.copy(alpha = 0.4f))
    ) {
        Box(
            modifier = Modifier.padding(vertical = 9.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = if (enabled) CyanAccent else TextMuted,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
private fun NavySurfaceVariant(): Color = NavySurface.copy(alpha = 0.95f)
