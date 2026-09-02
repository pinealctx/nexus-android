package com.pinealctx.nexus.util

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.pinealctx.nexus.core.AppPreferences
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Singleton
class LocaleManager @Inject constructor(
    private val appPreferences: AppPreferences
) {
    companion object {
        const val SYSTEM = "system"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val selectedLocale: Flow<String> = appPreferences.settings
        .map { it.localeTag }
        .distinctUntilChanged()

    suspend fun setLocale(localeTag: String) {
        appPreferences.setLocale(localeTag)
        applyLocale(localeTag)
    }

    private suspend fun applyLocale(localeTag: String) = withContext(Dispatchers.Main.immediate) {
        val appLocale = if (localeTag == SYSTEM) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(localeTag)
        }
        AppCompatDelegate.setApplicationLocales(appLocale)
    }

    fun restoreLocale() {
        scope.launch {
            val saved = selectedLocale.first()
            if (saved != SYSTEM) {
                applyLocale(saved)
            }
        }
    }
}
