package ru.sapn.vpn.ui.settings

import android.app.Application
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.sapn.vpn.data.local.SettingsStore
import ru.sapn.vpn.domain.model.AppRoutingMode

data class AppItem(val packageName: String, val label: String)

data class PerAppUiState(
    val mode: AppRoutingMode = AppRoutingMode.OFF,
    val selected: Set<String> = emptySet(),
    val apps: List<AppItem> = emptyList(),
    val query: String = "",
    val loading: Boolean = true,
)

class PerAppViewModel(
    app: Application,
    private val store: SettingsStore,
) : AndroidViewModel(app) {

    private val _ui = MutableStateFlow(PerAppUiState())
    val ui: StateFlow<PerAppUiState> = _ui.asStateFlow()

    fun load() {
        viewModelScope.launch {
            val settings = store.get()
            val apps = withContext(Dispatchers.IO) { loadApps() }
            _ui.value = PerAppUiState(
                mode = settings.appMode,
                selected = settings.appPackages.toSet(),
                apps = apps,
                loading = false,
            )
        }
    }

    private fun loadApps(): List<AppItem> {
        val pm = getApplication<Application>().packageManager
        val self = getApplication<Application>().packageName
        val installed = runCatching { pm.getInstalledApplications(0) }.getOrDefault(emptyList())
        return installed
            .asSequence()
            .filter { it.packageName != self }
            // Показываем только приложения, которые можно запустить (пользовательские).
            .filter { runCatching { pm.getLaunchIntentForPackage(it.packageName) != null }.getOrDefault(false) }
            .map { AppItem(it.packageName, runCatching { pm.getApplicationLabel(it).toString() }.getOrDefault(it.packageName)) }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    fun setMode(mode: AppRoutingMode) {
        _ui.value = _ui.value.copy(mode = mode)
        persist()
    }

    fun toggle(pkg: String) {
        val sel = _ui.value.selected.toMutableSet()
        if (!sel.add(pkg)) sel.remove(pkg)
        _ui.value = _ui.value.copy(selected = sel)
        persist()
    }

    fun setQuery(q: String) {
        _ui.value = _ui.value.copy(query = q)
    }

    private fun persist() {
        val s = _ui.value
        viewModelScope.launch {
            val cur = store.get()
            store.save(cur.copy(appMode = s.mode, appPackages = s.selected.toList()))
        }
    }

    class Factory(
        private val app: Application,
        private val store: SettingsStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PerAppViewModel(app, store) as T
    }
}
