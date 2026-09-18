package com.example.ui.plugins

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.SettingsDataStore
import com.example.data.model.PluginRegistry
import com.example.data.model.VipoPlugin
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PluginsViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsDataStore = SettingsDataStore(application)

    val plugins: List<VipoPlugin> = PluginRegistry.all

    val enabledIds: StateFlow<Set<String>> = settingsDataStore.enabledPluginIds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PluginRegistry.defaultEnabledIds)

    fun setEnabled(pluginId: String, enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setPluginEnabled(pluginId, enabled)
        }
    }

    fun resetToDefaults() {
        viewModelScope.launch {
            settingsDataStore.setEnabledPlugins(PluginRegistry.defaultEnabledIds)
        }
    }

    fun disableAll() {
        viewModelScope.launch {
            settingsDataStore.setEnabledPlugins(emptySet())
        }
    }
}
