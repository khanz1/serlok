package com.khanz1.serlok.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "serlok_prefs")

/**
 * Small app-wide preferences store. Compose-facing properties are observable while
 * DataStore remains the durable source of truth across process recreation.
 */
object AppSettings {
    val LOCATION_INTERVALS = listOf(2_000L, 5_000L, 10_000L, 30_000L, 60_000L)

    private object Keys {
        val LOC_INTERVAL_IDX = intPreferencesKey("loc_interval_idx")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val SESSION_TOKEN = stringPreferencesKey("session_token")
        val LEGACY_ACCESS_TOKEN = stringPreferencesKey("access_token")
        val LEGACY_REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val USER_ID = stringPreferencesKey("user_id")
        val USER_NAME = stringPreferencesKey("user_name")
        val USER_EMAIL = stringPreferencesKey("user_email")
        val DEVICE_INSTANCE_ID = stringPreferencesKey("device_instance_id")
        val GFORCE_DEBUG_OVERLAY = booleanPreferencesKey("gforce_debug_overlay")
    }

    private lateinit var ds: DataStore<Preferences>

    private var _locationIntervalIndex by mutableStateOf(2)
    private var _onboardingDone by mutableStateOf(false)
    private var _token by mutableStateOf("")
    private var _userId by mutableStateOf("")
    private var _userName by mutableStateOf("")
    private var _userEmail by mutableStateOf("")
    private var _deviceInstanceId by mutableStateOf("")
    private var _gforceDebugEnabled by mutableStateOf(false)

    val locationIntervalIndex: Int get() = _locationIntervalIndex
    val locationIntervalMs: Long get() = LOCATION_INTERVALS[_locationIntervalIndex]
    val onboardingDone: Boolean get() = _onboardingDone
    val token: String get() = _token
    val userId: String get() = _userId
    val userName: String get() = _userName
    val userEmail: String get() = _userEmail
    val deviceInstanceId: String get() = _deviceInstanceId
    val gforceDebugEnabled: Boolean get() = _gforceDebugEnabled
    val isLoggedIn: Boolean get() = _token.isNotEmpty()

    fun init(context: Context) {
        ds = context.applicationContext.dataStore
        val prefs = runBlocking { ds.data.first() }
        _locationIntervalIndex = (prefs[Keys.LOC_INTERVAL_IDX] ?: 2)
            .coerceIn(0, LOCATION_INTERVALS.lastIndex)
        _onboardingDone = prefs[Keys.ONBOARDING_DONE] ?: false
        _token = prefs[Keys.SESSION_TOKEN]
            ?: prefs[Keys.LEGACY_ACCESS_TOKEN]
            ?: ""
        _userId = prefs[Keys.USER_ID] ?: ""
        _userName = prefs[Keys.USER_NAME] ?: ""
        _userEmail = prefs[Keys.USER_EMAIL] ?: ""
        _deviceInstanceId = prefs[Keys.DEVICE_INSTANCE_ID] ?: "android-${UUID.randomUUID()}"
        _gforceDebugEnabled = prefs[Keys.GFORCE_DEBUG_OVERLAY] ?: false
        if (prefs[Keys.DEVICE_INSTANCE_ID].isNullOrBlank()) {
            runBlocking { ds.edit { it[Keys.DEVICE_INSTANCE_ID] = _deviceInstanceId } }
        }
        if (prefs[Keys.LEGACY_ACCESS_TOKEN] != null || prefs[Keys.LEGACY_REFRESH_TOKEN] != null) {
            runBlocking {
                ds.edit {
                    if (_token.isNotEmpty()) {
                        it[Keys.SESSION_TOKEN] = _token
                    }
                    it.remove(Keys.LEGACY_ACCESS_TOKEN)
                    it.remove(Keys.LEGACY_REFRESH_TOKEN)
                }
            }
        }
    }

    private fun io(block: suspend (MutablePreferences) -> Unit) =
        CoroutineScope(Dispatchers.IO).launch { ds.edit { block(it) } }

    fun setLocationIntervalIndex(index: Int) {
        _locationIntervalIndex = index.coerceIn(0, LOCATION_INTERVALS.lastIndex)
        io { it[Keys.LOC_INTERVAL_IDX] = _locationIntervalIndex }
    }

    fun setOnboardingDone() {
        _onboardingDone = true
        io { it[Keys.ONBOARDING_DONE] = true }
    }

    fun setGforceDebugEnabled(enabled: Boolean) {
        _gforceDebugEnabled = enabled
        io { it[Keys.GFORCE_DEBUG_OVERLAY] = enabled }
    }

    fun saveAuth(token: String, userId: String, name: String, email: String) {
        _token = token
        _userId = userId
        _userName = name
        _userEmail = email
        io {
            it[Keys.SESSION_TOKEN] = token
            it.remove(Keys.LEGACY_ACCESS_TOKEN)
            it.remove(Keys.LEGACY_REFRESH_TOKEN)
            it[Keys.USER_ID] = userId
            it[Keys.USER_NAME] = name
            it[Keys.USER_EMAIL] = email
        }
    }

    fun saveUserProfile(name: String, email: String) {
        _userName = name
        _userEmail = email
        io {
            it[Keys.USER_NAME] = name
            it[Keys.USER_EMAIL] = email
        }
    }

    fun clearAuth() {
        _token = ""
        _userId = ""
        _userName = ""
        _userEmail = ""
        io {
            it.remove(Keys.SESSION_TOKEN)
            it.remove(Keys.LEGACY_ACCESS_TOKEN)
            it.remove(Keys.LEGACY_REFRESH_TOKEN)
            it.remove(Keys.USER_ID)
            it.remove(Keys.USER_NAME)
            it.remove(Keys.USER_EMAIL)
        }
    }
}
