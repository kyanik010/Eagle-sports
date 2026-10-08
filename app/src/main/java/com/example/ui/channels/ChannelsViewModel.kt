package com.example.ui.channels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ChannelEntity
import com.example.data.preferences.PreferencesManager
import com.example.data.repository.ChannelRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ChannelSortOrder {
    NUMBER,
    NAME
}

class ChannelsViewModel(
    private val repository: ChannelRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _selectedGroup = MutableStateFlow("All")
    val selectedGroup: StateFlow<String> = _selectedGroup.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortOrder = MutableStateFlow(ChannelSortOrder.NUMBER)
    val sortOrder: StateFlow<ChannelSortOrder> = _sortOrder.asStateFlow()

    val groups: StateFlow<List<String>> = repository.allGroups
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val totalCount: StateFlow<Int> = repository.totalCount
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val favoritesFirst = preferencesManager.favoritesFirst
        .stateIn(viewModelScope, SharingStarted.Lazily, true)

    val showLogos = preferencesManager.showLogos
        .stateIn(viewModelScope, SharingStarted.Lazily, true)

    val showNumbers = preferencesManager.showChannelNumbers
        .stateIn(viewModelScope, SharingStarted.Lazily, true)

    @OptIn(ExperimentalCoroutinesApi::class)
    val channels: StateFlow<List<ChannelEntity>> = combine(
        _selectedGroup,
        _searchQuery,
        _sortOrder,
        favoritesFirst
    ) { group, query, sort, favFirst ->
        Quadruple(group, query, sort, favFirst)
    }.flatMapLatest { (group, query, sort, favFirst) ->
        val baseFlow = if (query.isNotBlank()) {
            repository.searchChannels(query.trim())
        } else {
            repository.getChannelsByGroup(group)
        }

        combine(baseFlow) { channelLists ->
            val list = channelLists[0]
            val comparator = when (sort) {
                ChannelSortOrder.NUMBER -> compareBy<ChannelEntity> { it.channelNumber }
                ChannelSortOrder.NAME -> compareBy { it.name.lowercase() }
            }

            if (favFirst) {
                list.sortedWith(
                    compareByDescending<ChannelEntity> { it.isFavorite }
                        .then(comparator)
                )
            } else {
                list.sortedWith(comparator)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteChannels: StateFlow<List<ChannelEntity>> = repository.favoriteChannels
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun selectGroup(group: String) {
        _selectedGroup.value = group
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun toggleSortOrder() {
        _sortOrder.value = if (_sortOrder.value == ChannelSortOrder.NUMBER) {
            ChannelSortOrder.NAME
        } else {
            ChannelSortOrder.NUMBER
        }
    }

    fun toggleFavorite(channelId: String) {
        viewModelScope.launch {
            repository.toggleFavorite(channelId)
        }
    }
}

data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
