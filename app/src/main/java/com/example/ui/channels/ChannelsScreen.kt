package com.example.ui.channels

import androidx.activity.compose.BackHandler

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
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
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

private val EagleBackground = Color(0xFF070B12)
private val EagleCard = Color(0xFF121A26)
private val EagleGold = Color(0xFFF5C211)
private val EagleCyan = Color(0xFF22D3EE)
private val EagleMuted = Color(0xFF64748B)
private val EagleBorder = Color(0xFF1E293B)
private val EagleText = Color(0xFFF1F5F9)
private val EagleGreen = Color(0xFF22C55E)

private val CategoryGradients = listOf(
    listOf(Color(0xFF1E1B4B), Color(0xFF0F141E)),
    listOf(Color(0xFF172554), Color(0xFF0F141E)),
    listOf(Color(0xFF4C1D24), Color(0xFF0F141E)),
    listOf(Color(0xFF14532D), Color(0xFF0F141E)),
    listOf(Color(0xFF422006), Color(0xFF0F141E)),
    listOf(Color(0xFF2E1065), Color(0xFF0F141E)),
    listOf(Color(0xFF4A044E), Color(0xFF0F141E)),
    listOf(Color(0xFF854D0E), Color(0xFF0F141E)),
    listOf(Color(0xFF831843), Color(0xFF0F141E)),
    listOf(Color(0xFF064E3B), Color(0xFF0F141E)),
    listOf(Color(0xFF1E3A8A), Color(0xFF0F141E)),
    listOf(Color(0xFF374151), Color(0xFF0F141E))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelsScreen(
    viewModel: ChannelsViewModel,
    isFavoritesOnly: Boolean = false,
    onChannelSelected: (ChannelEntity) -> Unit,
    onBack: () -> Unit,
    onNavigateSettings: () -> Unit,
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
    val totalCount by viewModel.totalCount.collectAsState()
    var activeCategory by remember { mutableStateOf(selectedGroup.takeIf { it != "All" }) }
    var showSearch by remember { mutableStateOf(searchQuery.isNotBlank()) }
    var sheetSearch by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val configuration = LocalConfiguration.current
    val sheetHeight = (configuration.screenHeightDp * 0.85f).dp
    val isTelevision = (configuration.uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK) == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
    val categoryFocusRequester = remember { FocusRequester() }
    val searchFocusRequester = remember { FocusRequester() }
    val channelFocusRequester = remember { FocusRequester() }

    LaunchedEffect(isTelevision, showSearch, activeCategory, groups.size) {
        if (isTelevision) {
            kotlinx.coroutines.delay(250)
            runCatching {
                when {
                    showSearch -> searchFocusRequester.requestFocus()
                    activeCategory != null -> channelFocusRequester.requestFocus()
                    groups.isNotEmpty() -> categoryFocusRequester.requestFocus()
                }
            }
        }
    }

    BackHandler(enabled = !isFavoritesOnly && showSearch) {
        showSearch = false
        viewModel.onSearchQueryChanged("")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(EagleBackground)
            .statusBarsPadding()
            .testTag(if (isFavoritesOnly) "favorites_screen" else "channels_screen")
    ) {
        if (isFavoritesOnly) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("channels_back_button")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = EagleGold)
                }
                Text(
                    text = "المفضلة",
                    modifier = Modifier.weight(1f),
                    color = EagleGold,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black
                )
                IconButton(onClick = { viewModel.toggleSortOrder() }, modifier = Modifier.testTag("sort_button")) {
                    Icon(
                        Icons.AutoMirrored.Filled.Sort,
                        contentDescription = "ترتيب",
                        tint = if (sortOrder == ChannelSortOrder.NAME) EagleCyan else EagleMuted
                    )
                }
            }
            OutlinedTextField(
                value = searchQuery,
                onValueChange = viewModel::onSearchQueryChanged,
                placeholder = { Text("ابحث باسم القناة أو رقمها...", color = EagleMuted) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = EagleGold) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "مسح", tint = EagleMuted)
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).testTag("channel_search_input"),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = EagleGold,
                    unfocusedBorderColor = EagleBorder,
                    focusedTextColor = EagleText,
                    unfocusedTextColor = EagleText,
                    cursorColor = EagleGold
                )
            )
            ChannelResults(
                channels = channels,
                isFavoritesOnly = true,
                listState = channelListState,
                showLogos = showLogos,
                showNumbers = showNumbers,
                onChannelSelected = onChannelSelected,
                onToggleFavorite = viewModel::toggleFavorite,
                modifier = Modifier.weight(1f)
            )
        } else if (showSearch) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        showSearch = false
                        viewModel.onSearchQueryChanged("")
                    },
                    modifier = Modifier.testTag("channels_search_back_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = EagleGold)
                }
                Text(
                    text = "البحث عن القنوات",
                    modifier = Modifier.weight(1f),
                    color = EagleGold,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "مسح البحث", tint = EagleMuted)
                    }
                }
            }
            OutlinedTextField(
                value = searchQuery,
                onValueChange = viewModel::onSearchQueryChanged,
                placeholder = { Text("ابحث باسم القناة أو رقمها...", color = EagleMuted) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = EagleGold) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                    .focusRequester(searchFocusRequester)
                    .testTag("channel_search_input"),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = EagleGold,
                    unfocusedBorderColor = EagleBorder,
                    focusedTextColor = EagleText,
                    unfocusedTextColor = EagleText,
                    cursorColor = EagleGold
                )
            )
            ChannelResults(
                channels = channels,
                isFavoritesOnly = false,
                listState = channelListState,
                showLogos = showLogos,
                showNumbers = showNumbers,
                onChannelSelected = onChannelSelected,
                onToggleFavorite = viewModel::toggleFavorite,
                modifier = Modifier.weight(1f)
            )
        } else {
            EagleHeader(onSearchClick = {
                showSearch = true
                viewModel.onSearchQueryChanged("")
            })
            HeroBanner(
                onWatchNow = { channels.firstOrNull()?.let(onChannelSelected) }
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.width(4.dp).height(14.dp).clip(RoundedCornerShape(4.dp)).background(EagleGold))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "تصنيفات القنوات",
                    color = EagleText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = "(${groups.size} تصنيف)",
                    color = EagleMuted,
                    fontSize = 11.sp
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "$totalCount قناة",
                    color = EagleGold,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (groups.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.LiveTv, contentDescription = null, tint = EagleMuted, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("لا توجد تصنيفات بعد", color = EagleMuted, fontSize = 14.sp)
                        Text("قم بمزامنة القنوات من الإعدادات", color = EagleMuted, fontSize = 11.sp)
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize().testTag("channel_categories"),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(groups, key = { _, group -> group }) { index, group ->
                        val categoryCount = if (selectedGroup == "All") {
                            channels.count { it.groupName == group }
                        } else if (selectedGroup == group) {
                            channels.size
                        } else {
                            null
                        }
                        CategoryCard(
                            title = group,
                            count = categoryCount,
                            index = index,
                            focusRequester = if (index == 0) categoryFocusRequester else null,
                            onClick = {
                                activeCategory = group
                                sheetSearch = ""
                                viewModel.onSearchQueryChanged("")
                                viewModel.selectGroup(group)
                            }
                        )
                    }
                }
            }
        }
    }

    if (!isFavoritesOnly && activeCategory != null) {
        ModalBottomSheet(
            onDismissRequest = {
                activeCategory = null
                sheetSearch = ""
                viewModel.onSearchQueryChanged("")
                viewModel.selectGroup("All")
            },
            sheetState = sheetState,
            containerColor = EagleBackground,
            contentColor = EagleText,
            dragHandle = {
                Box(
                    Modifier.padding(top = 10.dp, bottom = 14.dp)
                        .size(width = 40.dp, height = 4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(EagleBorder)
                )
            }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().height(sheetHeight).padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = activeCategory.orEmpty(),
                        modifier = Modifier.weight(1f),
                        color = EagleGold,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    IconButton(onClick = {
                        activeCategory = null
                        sheetSearch = ""
                        viewModel.onSearchQueryChanged("")
                        viewModel.selectGroup("All")
                    }, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.Clear, contentDescription = "إغلاق", tint = EagleMuted)
                    }
                }
                OutlinedTextField(
                    value = sheetSearch,
                    onValueChange = {
                        sheetSearch = it
                        viewModel.onSearchQueryChanged(it)
                    },
                    placeholder = { Text("ابحث عن قناة...", color = EagleMuted, fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = EagleGold, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (sheetSearch.isNotEmpty()) IconButton(onClick = {
                            sheetSearch = ""
                            viewModel.onSearchQueryChanged("")
                        }) {
                            Icon(Icons.Default.Clear, contentDescription = "مسح البحث", tint = EagleMuted)
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).testTag("channel_search_input"),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EagleGold,
                        unfocusedBorderColor = EagleBorder,
                        focusedTextColor = EagleText,
                        unfocusedTextColor = EagleText,
                        cursorColor = EagleGold
                    )
                )
                val sheetChannels = channels.filter { channel ->
                    sheetSearch.isBlank() ||
                        channel.name.contains(sheetSearch, ignoreCase = true) ||
                        channel.channelNumber.toString().contains(sheetSearch, ignoreCase = true) ||
                        (channel.epgCurrentTitle ?: channel.groupName).contains(sheetSearch, ignoreCase = true)
                }
                ChannelResults(
                    channels = sheetChannels,
                    isFavoritesOnly = false,
                    listState = channelListState,
                    showLogos = showLogos,
                    showNumbers = showNumbers,
                    onChannelSelected = onChannelSelected,
                    onToggleFavorite = viewModel::toggleFavorite,
                    modifier = Modifier.weight(1f),
                    firstItemFocusRequester = channelFocusRequester
                )
            }
        }
    }
}

@Composable
private fun EagleHeader(onSearchClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(EagleBackground.copy(alpha = 0.94f))
            .border(width = 0.5.dp, color = Color.White.copy(alpha = 0.05f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.LiveTv, contentDescription = null, tint = EagleGold, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(8.dp))
            Text("Eagle Sports", color = EagleGold, fontSize = 17.sp, fontWeight = FontWeight.Black)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconButton(onClick = onSearchClick, modifier = Modifier.size(32.dp).testTag("channels_header_search_button")) {
                Icon(Icons.Default.Search, contentDescription = "بحث", tint = EagleMuted, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onNavigateSettings, modifier = Modifier.size(32.dp).testTag("channels_header_settings_button")) {
                Icon(Icons.Default.Settings, contentDescription = "الإعدادات", tint = EagleMuted, modifier = Modifier.size(18.dp))
            }
            Box(modifier = Modifier.padding(start = 2.dp).size(32.dp)) {
                Box(
                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                        .background(Color(0xFF1E293B))
                        .border(2.dp, EagleGold, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("ES", color = EagleGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Box(
                    modifier = Modifier.align(Alignment.BottomEnd).size(8.dp)
                        .clip(CircleShape).background(EagleGreen)
                        .border(1.5.dp, EagleBackground, CircleShape)
                )
            }
        }
    }
}

@Composable
private fun HeroBanner(onWatchNow: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .height(95.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF1C2434), Color(0xFF0F141E))))
            .border(1.dp, EagleGold.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFFEF4444))
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(4.dp).clip(CircleShape).background(Color.White))
            Spacer(Modifier.width(4.dp))
            Text("مباشر الآن", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(2.dp))
        Text("beIN SPORTS 4K 1", color = EagleText, fontSize = 15.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Text(
            "مباراة ريال مدريد وبرشلونة - الدوري الإسباني",
            color = EagleMuted,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(5.dp))
        Row(
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(EagleGold)
                .focusable()
                .clickable(onClick = onWatchNow).padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = EagleBackground, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(4.dp))
            Text("شاهد الآن", color = EagleBackground, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CategoryCard(
    title: String,
    count: Int?,
    index: Int,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = when { pressed -> 0.97f; focused -> 1.02f; else -> 1f },
        animationSpec = tween(durationMillis = 120),
        label = "categoryPressScale"
    )
    val gradient = CategoryGradients[index % CategoryGradients.size]
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(animationSpec = tween(durationMillis = 350, delayMillis = index * 50)) +
            slideInVertically(
                animationSpec = tween(durationMillis = 350, delayMillis = index * 50),
                initialOffsetY = { it / 5 }
            )
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(95.dp).scale(scale)
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(gradient))
                .border(
                    width = when { pressed -> 2.dp; focused -> 3.dp; else -> 1.dp },
                    color = if (pressed || focused) EagleGold else EagleBorder,
                    shape = RoundedCornerShape(12.dp)
                )
                .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
                .focusable(interactionSource = interactionSource)
                .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
                .padding(10.dp)
                .testTag("category_card_$index")
        ) {
            if (focused) {
                Box(
                    modifier = Modifier.fillMaxSize()
                        .background(EagleGold.copy(alpha = 0.14f), RoundedCornerShape(10.dp))
                        .testTag("category_focus_highlight_$index")
                )
            }
            Icon(
                imageVector = if (index == 1) Icons.Default.SportsSoccer else Icons.Default.LiveTv,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.04f),
                modifier = Modifier.align(Alignment.BottomStart).size(65.dp)
            )
            Column(
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    color = EagleText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LiveTv, contentDescription = null, tint = EagleGold, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = count?.let { "$it قناة" } ?: "قنوات",
                        color = EagleGold,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
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
    onToggleFavorite: (String) -> Unit,
    modifier: Modifier = Modifier,
    firstItemFocusRequester: FocusRequester? = null
) {
    if (channels.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = if (isFavoritesOnly) Icons.Default.Star else Icons.Default.LiveTv,
                    contentDescription = null,
                    tint = EagleMuted,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = if (isFavoritesOnly) "لم تُضف أي قنوات إلى المفضلة بعد" else "لم يتم العثور على قنوات",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = if (isFavoritesOnly) "اضغط على رمز النجمة بجانب أي قناة لإضافتها إلى هنا" else "جرّب مسح البحث أو مزامنة القنوات من الإعدادات",
                    color = EagleMuted,
                    fontSize = 11.sp
                )
            }
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = modifier.fillMaxSize().testTag("channels_list"),
            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            itemsIndexed(items = channels, key = { _, channel -> channel.stableId }) { index, channel ->
                ChannelListItem(
                    channel = channel,
                    showLogo = showLogos,
                    showNumber = showNumbers,
                    onClick = { onChannelSelected(channel) },
                    onToggleFavorite = { onToggleFavorite(channel.stableId) },
                    focusRequester = if (index == 0) firstItemFocusRequester else null
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
    onToggleFavorite: () -> Unit,
    focusRequester: FocusRequester? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused = interactionSource.collectIsFocusedAsState().value
    val borderColor = if (isFocused) EagleGold else EagleBorder
    val containerBg = if (isFocused) Color(0xFF1A2536) else EagleCard

    Surface(
        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(10.dp)
            )
            .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .testTag("channel_item_${channel.channelNumber}"),
        shape = RoundedCornerShape(10.dp),
        color = containerBg
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showLogo) {
                Box(
                    modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF1E293B)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!channel.logoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = channel.logoUrl,
                            contentDescription = "شعار ${channel.name}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(29.dp)
                        )
                    } else {
                        Icon(Icons.Default.LiveTv, contentDescription = null, tint = EagleGold, modifier = Modifier.size(16.dp))
                    }
                }
                Spacer(Modifier.width(8.dp))
            }
            Column(modifier = Modifier.weight(1f).widthIn(min = 0.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = channel.name,
                    color = EagleText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (showNumber) {
                        Text(
                            text = "${channel.channelNumber}",
                            color = EagleGold,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(4.dp))
                                .background(EagleGold.copy(alpha = 0.1f))
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                            maxLines = 1
                        )
                    }
                    Text(
                        text = channel.epgCurrentTitle ?: channel.groupName,
                        color = EagleMuted,
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }
            Spacer(Modifier.width(4.dp))
            IconButton(
                onClick = onClick,
                modifier = Modifier.size(28.dp).clip(CircleShape).background(EagleGold.copy(alpha = 0.1f))
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "تشغيل ${channel.name}", tint = EagleGold, modifier = Modifier.size(16.dp))
            }
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.size(26.dp).testTag("fav_button_${channel.channelNumber}")
            ) {
                Icon(
                    imageVector = if (channel.isFavorite) Icons.Default.Star else Icons.Outlined.StarOutline,
                    contentDescription = if (channel.isFavorite) "إزالة من المفضلة" else "إضافة إلى المفضلة",
                    tint = if (channel.isFavorite) EagleCyan else EagleMuted,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
