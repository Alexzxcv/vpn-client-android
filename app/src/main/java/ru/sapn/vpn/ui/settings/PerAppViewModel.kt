package ru.sapn.vpn.ui.settings

import android.app.Application
import android.content.Intent
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
        // ОДИН запрос launcher-активностей вместо getLaunchIntentForPackage на
        // каждое приложение (там — отдельный IPC к PackageManager на пакет, что и
        // тормозило загрузку на устройствах с сотнями приложений). queryIntentActivities
        // сразу отдаёт запускаемые приложения с ярлыками.
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = runCatching { pm.queryIntentActivities(intent, 0) }.getOrDefault(emptyList())
        return resolved
            .asSequence()
            .filter { it.activityInfo.packageName != self }
            .map { ri ->
                // Ярлык берём из уже полученного ResolveInfo (без доп. IPC на пакет).
                val label = runCatching { ri.loadLabel(pm).toString() }
                    .getOrDefault(ri.activityInfo.packageName)
                AppItem(ri.activityInfo.packageName, label)
            }
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
