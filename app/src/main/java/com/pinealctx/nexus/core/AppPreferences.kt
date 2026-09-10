package com.pinealctx.nexus.core

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.appPreferencesDataStore by preferencesDataStore(name = "nexus_settings")

data class AppSettings(
    val localeTag: String = "system",
    val notificationAlerts: Boolean = true,
    val notificationSound: Boolean = true,
    val notificationPreview: Boolean = true,
    val notificationFriends: Boolean = true,
    val notificationReactions: Boolean = false
)

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private companion object {
        val LocaleTag = stringPreferencesKey("app_locale")
        val NotificationAlerts = booleanPreferencesKey("notification_alerts")
        val NotificationSound = booleanPreferencesKey("notification_sound")
        val NotificationPreview = booleanPreferencesKey("notification_preview")
        val NotificationFriends = booleanPreferencesKey("notification_friends")
        val NotificationReactions = booleanPreferencesKey("notification_reactions")
    }

    val settings: Flow<AppSettings> = context.appPreferencesDataStore.data
        .catch { error ->
            if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences())
            else throw error
        }
        .map { preferences ->
            AppSettings(
                localeTag = preferences[LocaleTag] ?: "system",
                notificationAlerts = preferences[NotificationAlerts] ?: true,
                notificationSound = preferences[NotificationSound] ?: true,
                notificationPreview = preferences[NotificationPreview] ?: true,
                notificationFriends = preferences[NotificationFriends] ?: true,
                notificationReactions = preferences[NotificationReactions] ?: false
            )
        }

    suspend fun setLocale(localeTag: String) {
        context.appPreferencesDataStore.edit { it[LocaleTag] = localeTag }
    }

    suspend fun setNotificationAlerts(enabled: Boolean) {
        context.appPreferencesDataStore.edit { it[NotificationAlerts] = enabled }
    }

    suspend fun setNotificationSound(enabled: Boolean) {
        context.appPreferencesDataStore.edit { it[NotificationSound] = enabled }
    }

    suspend fun currentSettings(): AppSettings = settings.first()

    suspend fun setNotificationPreview(enabled: Boolean) { context.appPreferencesDataStore.edit { it[NotificationPreview] = enabled } }
    suspend fun setNotificationFriends(enabled: Boolean) { context.appPreferencesDataStore.edit { it[NotificationFriends] = enabled } }
    suspend fun setNotificationReactions(enabled: Boolean) { context.appPreferencesDataStore.edit { it[NotificationReactions] = enabled } }
}
