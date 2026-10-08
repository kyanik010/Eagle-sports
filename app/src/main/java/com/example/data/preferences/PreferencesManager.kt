package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.UserSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "eagle_sports_prefs")

class PreferencesManager(private val context: Context) {

    companion object {
        val KEY_IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
        val KEY_USERNAME = stringPreferencesKey("username")
        val KEY_PASSWORD = stringPreferencesKey("password")
        val KEY_AUTH_TOKEN = stringPreferencesKey("auth_token")
        val KEY_IPTV_HOST = stringPreferencesKey("iptv_host")
        val KEY_SUB_STATUS = stringPreferencesKey("sub_status")
        val KEY_EXPIRY_DATE = stringPreferencesKey("expiry_date")
        val KEY_EXTERNAL_AUDIO_URL = stringPreferencesKey("external_audio_url")
        val KEY_LAST_SYNC_TIME = longPreferencesKey("last_sync_time")

        // Playback settings
        val KEY_HW_DECODING = booleanPreferencesKey("hw_decoding")
        val KEY_AUTO_RECONNECT = booleanPreferencesKey("auto_reconnect")
        val KEY_KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val KEY_LAST_CHANNEL_ID = stringPreferencesKey("last_channel_id")
        val KEY_CONFIRM_EXIT = booleanPreferencesKey("confirm_exit")

        // Audio settings
        val KEY_DEFAULT_AUDIO_SOURCE = stringPreferencesKey("default_audio_source") // "original" or "external"
        val KEY_EXTERNAL_DELAY_MS = intPreferencesKey("external_delay_ms")
        val KEY_REMEMBER_AUDIO_PREF = booleanPreferencesKey("remember_audio_pref")

        // Channels settings
        val KEY_FAVORITES_FIRST = booleanPreferencesKey("favorites_first")
        val KEY_SHOW_LOGOS = booleanPreferencesKey("show_logos")
        val KEY_SHOW_CHANNEL_NUMBERS = booleanPreferencesKey("show_channel_numbers")
        val KEY_SORT_ORDER = stringPreferencesKey("sort_order") // "number", "name"
    }

    val isLoggedIn: Flow<Boolean> = context.dataStore.data.map { it[KEY_IS_LOGGED_IN] ?: false }

    val userSession: Flow<UserSession?> = context.dataStore.data.map { prefs ->
        val loggedIn = prefs[KEY_IS_LOGGED_IN] ?: false
        if (!loggedIn) null
        else {
            UserSession(
                username = prefs[KEY_USERNAME] ?: "",
                password = prefs[KEY_PASSWORD],
                token = prefs[KEY_AUTH_TOKEN],
                iptvHost = prefs[KEY_IPTV_HOST] ?: "",
                status = prefs[KEY_SUB_STATUS] ?: "Active",
                expiryDate = prefs[KEY_EXPIRY_DATE] ?: "Never",
                externalAudioUrl = prefs[KEY_EXTERNAL_AUDIO_URL]
            )
        }
    }

    val lastSyncTime: Flow<Long> = context.dataStore.data.map { it[KEY_LAST_SYNC_TIME] ?: 0L }
    val keepScreenOn: Flow<Boolean> = context.dataStore.data.map { it[KEY_KEEP_SCREEN_ON] ?: true }
    val autoReconnect: Flow<Boolean> = context.dataStore.data.map { it[KEY_AUTO_RECONNECT] ?: true }
    val hwDecoding: Flow<Boolean> = context.dataStore.data.map { it[KEY_HW_DECODING] ?: true }
    val lastChannelId: Flow<String?> = context.dataStore.data.map { it[KEY_LAST_CHANNEL_ID] }
    val defaultAudioSource: Flow<String> = context.dataStore.data.map { it[KEY_DEFAULT_AUDIO_SOURCE] ?: "original" }
    val externalAudioDelayMs: Flow<Int> = context.dataStore.data.map { it[KEY_EXTERNAL_DELAY_MS] ?: 0 }
    val favoritesFirst: Flow<Boolean> = context.dataStore.data.map { it[KEY_FAVORITES_FIRST] ?: true }
    val showLogos: Flow<Boolean> = context.dataStore.data.map { it[KEY_SHOW_LOGOS] ?: true }
    val showChannelNumbers: Flow<Boolean> = context.dataStore.data.map { it[KEY_SHOW_CHANNEL_NUMBERS] ?: true }

    suspend fun saveSession(session: UserSession) {
        context.dataStore.edit { prefs ->
            prefs[KEY_IS_LOGGED_IN] = true
            prefs[KEY_USERNAME] = session.username
            session.password?.let { prefs[KEY_PASSWORD] = it }
            session.token?.let { prefs[KEY_AUTH_TOKEN] = it }
            prefs[KEY_IPTV_HOST] = session.iptvHost
            prefs[KEY_SUB_STATUS] = session.status
            prefs[KEY_EXPIRY_DATE] = session.expiryDate
            session.externalAudioUrl?.let { prefs[KEY_EXTERNAL_AUDIO_URL] = it }
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit { prefs ->
            prefs[KEY_IS_LOGGED_IN] = false
            prefs.remove(KEY_USERNAME)
            prefs.remove(KEY_PASSWORD)
            prefs.remove(KEY_AUTH_TOKEN)
            prefs.remove(KEY_IPTV_HOST)
            prefs.remove(KEY_SUB_STATUS)
            prefs.remove(KEY_EXPIRY_DATE)
            prefs.remove(KEY_EXTERNAL_AUDIO_URL)
            prefs.remove(KEY_LAST_CHANNEL_ID)
        }
    }

    suspend fun setLastSyncTime(timestamp: Long) {
        context.dataStore.edit { it[KEY_LAST_SYNC_TIME] = timestamp }
    }

    suspend fun setLastChannelId(id: String) {
        context.dataStore.edit { it[KEY_LAST_CHANNEL_ID] = id }
    }

    suspend fun setExternalAudioDelayMs(delay: Int) {
        context.dataStore.edit { it[KEY_EXTERNAL_DELAY_MS] = delay }
    }

    suspend fun setFavoritesFirst(enabled: Boolean) {
        context.dataStore.edit { it[KEY_FAVORITES_FIRST] = enabled }
    }

    suspend fun setKeepScreenOn(enabled: Boolean) {
        context.dataStore.edit { it[KEY_KEEP_SCREEN_ON] = enabled }
    }

    suspend fun setAutoReconnect(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_RECONNECT] = enabled }
    }

    suspend fun setHwDecoding(enabled: Boolean) {
        context.dataStore.edit { it[KEY_HW_DECODING] = enabled }
    }

    suspend fun setShowLogos(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SHOW_LOGOS] = enabled }
    }

    suspend fun setShowChannelNumbers(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SHOW_CHANNEL_NUMBERS] = enabled }
    }
}
