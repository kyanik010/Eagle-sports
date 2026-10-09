package com.example.ui.home

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.ChannelEntity
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.GreenLive
import com.example.ui.theme.NavyBorder
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.NavySurface
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun HomeScreen(
    channelCount: Int,
    favoriteCount: Int,
    username: String,
    featuredChannels: List<ChannelEntity>,
    onFeaturedChannelSelected: (ChannelEntity) -> Unit,
    onNavigateChannels: () -> Unit,
    onNavigateFavorites: () -> Unit,
    onNavigateSettings: () -> Unit
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyDeep)
            .testTag("home_screen")
    ) {
        Image(
            painter = painterResource(id = R.drawable.stadium_hero_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            alpha = 0.24f
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            NavyDeep.copy(alpha = 0.82f),
                            NavyDark.copy(alpha = 0.94f),
                            NavyDeep
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = if (isLandscape) 24.dp else 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Header(username = username, onSettingsClick = onNavigateSettings)
            Spacer(Modifier.height(if (isLandscape) 12.dp else 16.dp))

            if (isLandscape) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.weight(1.25f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        HeroBanner(onClick = onNavigateChannels, height = 184)
                        SectionHeading(title = "قنوات مباشرة", trailing = "عرض الكل", onTrailingClick = onNavigateChannels)
                        if (featuredChannels.isEmpty()) {
                            EmptyChannelsHint()
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                featuredChannels.take(6).forEach { channel ->
                                    FeaturedChannelCard(
                                        channel = channel,
                                        modifier = Modifier.fillMaxWidth().height(62.dp),
                                        onClick = { onFeaturedChannelSelected(channel) }
                                    )
                                }
                            }
                        }
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        HomeMenuCard(
                            title = "القنوات",
                            subtitle = "$channelCount قناة بث مباشر متاحة",
                            icon = Icons.Default.LiveTv,
                            accentColor = GoldPrimary,
                            testTag = "menu_channels",
                            compact = true,
                            onClick = onNavigateChannels
                        )
                        HomeMenuCard(
                            title = "المفضلة",
                            subtitle = "$favoriteCount قناة محفوظة في المفضلة",
                            icon = Icons.Default.Star,
                            accentColor = CyanAccent,
                            testTag = "menu_favorites",
                            compact = true,
                            onClick = onNavigateFavorites
                        )
                        HomeMenuCard(
                            title = "الإعدادات",
                            subtitle = "التشغيل والصوت والمزامنة",
                            icon = Icons.Default.Settings,
                            accentColor = TextSecondary,
                            testTag = "menu_settings",
                            compact = true,
                            onClick = onNavigateSettings
                        )
                    }
                }
            } else {
                HeroBanner(onClick = onNavigateChannels, height = 172)
                Spacer(Modifier.height(18.dp))
                SectionHeading(title = "قنوات مباشرة", trailing = "عرض الكل", onTrailingClick = onNavigateChannels)
                Spacer(Modifier.height(10.dp))
                if (featuredChannels.isEmpty()) {
                    EmptyChannelsHint()
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        featuredChannels.take(6).forEach { channel ->
                            FeaturedChannelCard(
                                channel = channel,
                                modifier = Modifier.width(188.dp).height(82.dp),
                                onClick = { onFeaturedChannelSelected(channel) }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                Column(
                    modifier = Modifier.widthIn(max = 620.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HomeMenuCard(
                        title = "القنوات",
                        subtitle = "$channelCount قناة بث مباشر متاحة",
                        icon = Icons.Default.LiveTv,
                        accentColor = GoldPrimary,
                        testTag = "menu_channels",
                        onClick = onNavigateChannels
                    )
                    HomeMenuCard(
                        title = "المفضلة",
                        subtitle = "$favoriteCount قناة محفوظة في المفضلة",
                        icon = Icons.Default.Star,
                        accentColor = CyanAccent,
                        testTag = "menu_favorites",
                        onClick = onNavigateFavorites
                    )
                    HomeMenuCard(
                        title = "الإعدادات",
                        subtitle = "التشغيل والصوت والمزامنة",
                        icon = Icons.Default.Settings,
                        accentColor = TextSecondary,
                        testTag = "menu_settings",
                        onClick = onNavigateSettings
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Text(
                text = "EAGLE SPORTS  •  بث مباشر متعدد المصادر",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = TextMuted,
                    letterSpacing = 1.sp
                )
            )
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun Header(username: String, onSettingsClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(NavyDark)
                    .border(1.5.dp, GoldPrimary.copy(alpha = 0.7f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.eagle_sports_icon),
                    contentDescription = "Eagle Sports",
                    modifier = Modifier.size(43.dp),
                    contentScale = ContentScale.Crop
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = "Eagle Sports",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = GoldPrimary,
                        letterSpacing = 0.4.sp
                    )
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(GreenLive))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "البث المباشر • $username",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = TextSecondary,
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(NavySurface.copy(alpha = 0.9f))
                .border(1.dp, GoldPrimary.copy(alpha = 0.35f), CircleShape)
                .clickable(onClick = onSettingsClick)
                .focusable(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Settings,
                contentDescription = "الإعدادات",
                tint = GoldPrimary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun HeroBanner(onClick: () -> Unit, height: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(RoundedCornerShape(22.dp))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .testTag("home_hero_banner")
    ) {
        Image(
            painter = painterResource(id = R.drawable.stadium_hero_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color(0xD907090F), NavyDeep.copy(alpha = 0.98f))
                )
            )
        )
        Column(
            modifier = Modifier.align(Alignment.BottomEnd).padding(18.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = "EAGLE SPORTS • LIVE",
                color = Color.Black,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(GoldPrimary).padding(horizontal = 10.dp, vertical = 5.dp)
            )
            Spacer(Modifier.height(7.dp))
            Text(
                text = "عالم الرياضة بين يديك",
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End
            )
            Spacer(Modifier.height(9.dp))
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f))
                    .border(1.dp, Color.White.copy(alpha = 0.24f), CircleShape)
                    .padding(horizontal = 13.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text("استعرض القنوات", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun SectionHeading(title: String, trailing: String, onTrailingClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(
            trailing,
            color = GoldPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onTrailingClick).padding(horizontal = 8.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun FeaturedChannelCard(
    channel: ChannelEntity,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xCC141923))
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(GoldPrimary.copy(alpha = 0.13f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.LiveTv, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.name,
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = channel.groupName.takeIf { it.isNotBlank() && it != "All" } ?: "قناة بث مباشر",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(Icons.Default.ChevronLeft, contentDescription = "تشغيل القناة", tint = TextSecondary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun EmptyChannelsHint() {
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.04f)).padding(14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text("ستظهر هنا قنواتك بعد مزامنة الاشتراك", color = TextSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun HomeMenuCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    testTag: String,
    compact: Boolean = false,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused = interactionSource.collectIsFocusedAsState().value
    val borderColor = if (isFocused) GoldPrimary else NavyBorder.copy(alpha = 0.72f)
    val cardBackground = if (isFocused) NavySurface.copy(alpha = 0.98f) else NavyDark.copy(alpha = 0.88f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (compact) 78.dp else 82.dp)
            .clip(RoundedCornerShape(18.dp))
            .border(if (isFocused) 2.dp else 1.dp, borderColor, RoundedCornerShape(18.dp))
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .testTag(testTag),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = cardBackground)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = if (compact) 12.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(if (compact) 42.dp else 46.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(accentColor.copy(alpha = 0.13f))
                    .border(1.dp, accentColor.copy(alpha = 0.22f), RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(if (compact) 10.dp else 14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    subtitle,
                    color = TextSecondary,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier.size(27.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.04f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(18.dp))
            }
        }
    }
}
