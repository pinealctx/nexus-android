package com.pinealctx.nexus.util

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocaleManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val SYSTEM = "system"
        private const val PREF_NAME = "nexus_settings"
        private const val PREF_KEY = "app_locale"
    }

    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun getSelectedLocale(): String = prefs.getString(PREF_KEY, SYSTEM) ?: SYSTEM

    fun setLocale(localeTag: String) {
        prefs.edit().putString(PREF_KEY, localeTag).apply()
        val appLocale = if (localeTag == SYSTEM) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(localeTag)
        }
        AppCompatDelegate.setApplicationLocales(appLocale)
    }

    fun restoreLocale() {
        val saved = getSelectedLocale()
        if (saved != SYSTEM) {
            AppCompatDelegate.setApplicationLocales(
                LocaleListCompat.forLanguageTags(saved)
            )
        }
    }
}
