package io.karpilabs.simplemp3.data.jellyfin

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.jellyfinDataStore by preferencesDataStore(name = "jellyfin_prefs")

@Singleton
class JellyfinPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val SERVER_URL = stringPreferencesKey("server_url")
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val USER_ID = stringPreferencesKey("user_id")
        val USER_NAME = stringPreferencesKey("user_name")
        val SERVER_ID = stringPreferencesKey("server_id")
        val DEVICE_ID = stringPreferencesKey("device_id")
    }

    val sessionFlow: Flow<JellyfinSession?> = context.jellyfinDataStore.data.map { prefs ->
        val url = prefs[Keys.SERVER_URL]
        val token = prefs[Keys.ACCESS_TOKEN]
        val userId = prefs[Keys.USER_ID]
        val userName = prefs[Keys.USER_NAME]
        val deviceId = prefs[Keys.DEVICE_ID]
        if (url.isNullOrBlank() || token.isNullOrBlank() || userId.isNullOrBlank() || deviceId.isNullOrBlank()) {
            null
        } else {
            JellyfinSession(
                serverUrl = url,
                accessToken = token,
                userId = userId,
                userName = userName.orEmpty(),
                serverId = prefs[Keys.SERVER_ID],
                deviceId = deviceId
            )
        }
    }

    suspend fun getSession(): JellyfinSession? = sessionFlow.first()

    suspend fun getOrCreateDeviceId(): String {
        val existing = context.jellyfinDataStore.data.first()[Keys.DEVICE_ID]
        if (!existing.isNullOrBlank()) return existing
        val id = UUID.randomUUID().toString()
        context.jellyfinDataStore.edit { it[Keys.DEVICE_ID] = id }
        return id
    }

    suspend fun saveSession(session: JellyfinSession) {
        context.jellyfinDataStore.edit { prefs ->
            prefs[Keys.SERVER_URL] = session.serverUrl
            prefs[Keys.ACCESS_TOKEN] = session.accessToken
            prefs[Keys.USER_ID] = session.userId
            prefs[Keys.USER_NAME] = session.userName
            session.serverId?.let { prefs[Keys.SERVER_ID] = it }
            prefs[Keys.DEVICE_ID] = session.deviceId
        }
    }

    suspend fun clearSession() {
        context.jellyfinDataStore.edit { prefs ->
            prefs.remove(Keys.SERVER_URL)
            prefs.remove(Keys.ACCESS_TOKEN)
            prefs.remove(Keys.USER_ID)
            prefs.remove(Keys.USER_NAME)
            prefs.remove(Keys.SERVER_ID)
            // keep device id
        }
    }
}
