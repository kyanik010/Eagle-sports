package com.example.ui.channels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.ChannelEntity
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.NavyBorder
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.NavySurface
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun ChannelsScreen(
    viewModel: ChannelsViewModel,
    isFavoritesOnly: Boolean = false,
    onChannelSelected: (ChannelEntity) -> Unit,
    onBack: () -> Unit,
    channelListState: LazyListState,
    groupListState: LazyListState
) {
    val channels by if (isFavoritesOnly) viewModel.favoriteChannels.collectAsState() else viewModel.channels.collectAsState()
    val groups by viewModel.groups.collectAsState()
    val selectedGroup by viewModel.selectedGroup.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val showLogos by viewModel.showLogos.collectAsState()
    val showNumbers by viewModel.showNumbers.collectAsState()


    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyDeep)
            .statusBarsPadding()
            .testTag(if (isFavoritesOnly) "favorites_screen" else "channels_screen")
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("channels_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "رجوع",
                    tint = GoldPrimary
                )
            }

            Text(
                text = if (isFavoritesOnly) "المفضلة" else "القنوات المباشرة",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = GoldPrimary
                ),
                modifier = Modifier.weight(1f)
            )

            // Sort Toggle
            IconButton(
                onClick = { viewModel.toggleSortOrder() },
                modifier = Modifier.testTag("sort_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Sort,
                    contentDescription = "ترتيب",
                    tint = if (sortOrder == ChannelSortOrder.NAME) CyanAccent else TextSecondary
                )
            }
        }

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.onSearchQueryChanged(it) },
            placeholder = { Text("ابحث باسم القناة أو رقمها...", color = TextMuted) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = GoldPrimary)
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "مسح", tint = TextSecondary)
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .testTag("channel_search_input"),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GoldPrimary,
                unfocusedBorderColor = NavyBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                cursorColor = GoldPrimary
            )
        )

        // Vertical category sidebar. Keeping this state in MainActivity preserves
        // the category scroll position when returning from the player.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (!isFavoritesOnly && groups.isNotEmpty()) {
                val allGroupsList = listOf("All") + groups
                LazyColumn(
                    state = groupListState,
                    modifier = Modifier
                        .width(148.dp)
                        .fillMaxHeight()
                        .background(NavyDark)
                        .testTag("channel_categories"),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(allGroupsList, key = { it }) { group ->
                        val isSelected = group == selectedGroup
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) GoldPrimary else NavySurface)
                                .clickable { viewModel.selectGroup(group) }
                                .padding(horizontal = 10.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (group == "All") "الكل" else group,
                                color = if (isSelected) NavyDeep else TextPrimary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    ChannelResults(
                        channels = channels,
                        isFavoritesOnly = false,
                        listState = channelListState,
                        showLogos = showLogos,
                        showNumbers = showNumbers,
                        onChannelSelected = onChannelSelected,
                        onToggleFavorite = { viewModel.toggleFavorite(it) }
                    )
                }
            } else {
                ChannelResults(
                    channels = channels,
                    isFavoritesOnly = isFavoritesOnly,
                    listState = channelListState,
                    showLogos = showLogos,
                    showNumbers = showNumbers,
                    onChannelSelected = onChannelSelected,
                    onToggleFavorite = { viewModel.toggleFavorite(it) }
                )
            }
        }
    }
}


@Composable
private fun ChannelResults(
    channels: List<ChannelEntity>,
    isFavoritesOnly: Boolean,
    listState: LazyListState,
    showLogos: Boolean,
    showNumbers: Boolean,
    onChannelSelected: (ChannelEntity) -> Unit,
    onToggleFavorite: (String) -> Unit
) {
    if (channels.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = if (isFavoritesOnly) Icons.Default.Star else Icons.Default.LiveTv,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = if (isFavoritesOnly) "لم تُضف أي قنوات إلى المفضلة بعد" else "لم يتم العثور على قنوات",
                    style = MaterialTheme.typography.bodyLarge.copy(color = TextSecondary),
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = if (isFavoritesOnly) "اضغط على رمز النجمة بجانب أي قناة لإضافتها إلى هنا" else "جرّب مسح البحث أو مزامنة القنوات من الإعدادات",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
                )
            }
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .testTag("channels_list"),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(items = channels, key = { it.stableId }) { channel ->
                ChannelListItem(
                    channel = channel,
                    showLogo = showLogos,
                    showNumber = showNumbers,
                    onClick = { onChannelSelected(channel) },
                    onToggleFavorite = { onToggleFavorite(channel.stableId) }
                )
            }
        }
    }
}

@Composable
fun ChannelListItem(
    channel: ChannelEntity,
    showLogo: Boolean,
    showNumber: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused = interactionSource.collectIsFocusedAsState().value

    val borderColor = if (isFocused) GoldPrimary else NavyBorder.copy(alpha = 0.5f)
    val containerBg = if (isFocused) NavySurface.copy(alpha = 0.95f) else NavyDark.copy(alpha = 0.85f)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(14.dp)
            )
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .testTag("channel_item_${channel.channelNumber}"),
        shape = RoundedCornerShape(14.dp),
        color = containerBg
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Channel Number
            if (showNumber) {
                Text(
                    text = "${channel.channelNumber}",
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = GoldPrimary,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.width(36.dp)
                )
            }

            // Logo or fallback
            if (showLogo) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(NavySurface),
                    contentAlignment = Alignment.Center
                ) {
                    if (!channel.logoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = channel.logoUrl,
                            contentDescription = "شعار ${channel.name}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(38.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.LiveTv,
                            contentDescription = null,
                            tint = CyanAccent,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
            }

            // Name & Current EPG / Group
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = channel.epgCurrentTitle ?: channel.groupName,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = if (channel.epgCurrentTitle != null) CyanAccent else TextSecondary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Favorite Pin Button
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier
                    .size(44.dp)
                    .testTag("fav_button_${channel.channelNumber}")
            ) {
                Icon(
                    imageVector = if (channel.isFavorite) Icons.Default.Star else Icons.Outlined.StarOutline,
                    contentDescription = if (channel.isFavorite) "إزالة من المفضلة" else "إضافة إلى المفضلة",
                    tint = if (channel.isFavorite) GoldPrimary else TextMuted
                )
            }
        }
    }
}
