package com.pinealctx.nexus.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.pinealctx.nexus.R
import com.pinealctx.nexus.util.LocaleManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LanguageSettingsViewModel @Inject constructor(
    private val localeManager: LocaleManager
) : ViewModel() {

    val selectedLocale: StateFlow<String> = localeManager.selectedLocale.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LocaleManager.SYSTEM
    )

    fun setLocale(tag: String) {
        viewModelScope.launch { localeManager.setLocale(tag) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSettingsScreen(
    onBack: () -> Unit,
    viewModel: LanguageSettingsViewModel = hiltViewModel()
) {
    val selectedLocale by viewModel.selectedLocale.collectAsState()

    val options = listOf(
        LocaleManager.SYSTEM to R.string.language_system,
        "en" to R.string.language_en,
        "zh-CN" to R.string.language_zh
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.language_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            options.forEach { (tag, labelRes) ->
                ListItem(
                    headlineContent = { Text(stringResource(labelRes)) },
                    trailingContent = {
                        RadioButton(
                            selected = selectedLocale == tag,
                            onClick = { viewModel.setLocale(tag) }
                        )
                    },
                    modifier = Modifier.clickable { viewModel.setLocale(tag) }
                )
                HorizontalDivider()
            }
        }
    }
}
