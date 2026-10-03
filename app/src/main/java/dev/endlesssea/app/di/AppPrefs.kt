package dev.endlesssea.app.di

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Préférences UI/téléchargement/lecteur (SharedPreferences léger, aucun DataStore requis).
 * Chaque écriture met à jour un StateFlow → Compose se recompose instantanément.
 */
@Singleton
class AppPrefs @Inject constructor(@ApplicationContext context: Context) {

    companion object {
        // Thème : 0 = système, 1 = clair, 2 = sombre, 3 = AMOLED (noir pur)
        const val THEME_SYSTEM = 0
        const val THEME_LIGHT = 1
        const val THEME_DARK = 2
        const val THEME_AMOLED = 3

        // Toutes les destinations possibles de la barre
        val ALL_TAB_ROUTES = listOf("home", "explore", "search", "library", "downloads")
        val DEFAULT_TABS = setOf("home", "explore", "search", "library", "downloads")
    }

    private val p = context.getSharedPreferences("endless_sea_prefs", Context.MODE_PRIVATE)

    // ---------------------------------------------------------------- thème
    // AMOLED par défaut (esthétique cible) ; l'utilisateur peut revenir à Système.
    private val _themeMode = MutableStateFlow(p.getInt("theme_mode", THEME_AMOLED))
    val themeMode: StateFlow<Int> = _themeMode
    fun setThemeMode(mode: Int) {
        p.edit().putInt("theme_mode", mode).apply(); _themeMode.value = mode
    }

    // --------------------------------------------------------- barre flottante
    private val _barTabs = MutableStateFlow(
        p.getStringSet("bar_tabs", DEFAULT_TABS)?.toSet() ?: DEFAULT_TABS,
    )
    val barTabs: StateFlow<Set<String>> = _barTabs
    fun setBarTab(route: String, enabled: Boolean) {
        val next = (_barTabs.value + if (enabled) setOf(route) else emptySet()) -
            if (enabled) emptySet() else setOf(route)
        // jamais moins d'un onglet
        val safe = if (next.isEmpty()) setOf("home") else next
        p.edit().putStringSet("bar_tabs", safe).apply(); _barTabs.value = safe
    }

    // ---------------------------------------------------------------- stockage
    private val _storageUri = MutableStateFlow(p.getString("storage_uri", null))
    val storageUri: StateFlow<String?> = _storageUri
    fun setStorageUri(uri: String?) {
        p.edit().apply { if (uri == null) remove("storage_uri") else putString("storage_uri", uri) }.apply()
        _storageUri.value = uri
    }

    // ---------------------------------------------------------------- téléchargement
    private val _wifiOnly = MutableStateFlow(p.getBoolean("wifi_only", true))
    val wifiOnly: StateFlow<Boolean> = _wifiOnly
    fun setWifiOnly(v: Boolean) { p.edit().putBoolean("wifi_only", v).apply(); _wifiOnly.value = v }

    private val _partsPerTask = MutableStateFlow(p.getInt("parts", 4))
    val partsPerTask: StateFlow<Int> = _partsPerTask
    fun setPartsPerTask(v: Int) { val c = v.coerceIn(1, 16); p.edit().putInt("parts", c).apply(); _partsPerTask.value = c }

    private val _parallelTasks = MutableStateFlow(p.getInt("tasks", 2))
    val parallelTasks: StateFlow<Int> = _parallelTasks
    fun setParallelTasks(v: Int) { val c = v.coerceIn(1, 4); p.edit().putInt("tasks", c).apply(); _parallelTasks.value = c }

    // ---------------------------------------------------------------- mises à jour
    private val _updateAutoCheck = MutableStateFlow(p.getBoolean("update_auto_check", true))
    val updateAutoCheck: StateFlow<Boolean> = _updateAutoCheck
    fun setUpdateAutoCheck(v: Boolean) { p.edit().putBoolean("update_auto_check", v).apply(); _updateAutoCheck.value = v }

    // ---------------------------------------------------------------- lecteur
    private val _defaultSpeed = MutableStateFlow(p.getFloat("speed", 1f))
    val defaultSpeed: StateFlow<Float> = _defaultSpeed
    fun setDefaultSpeed(v: Float) { val c = v.coerceIn(0.25f, 3f); p.edit().putFloat("speed", c).apply(); _defaultSpeed.value = c }

    private val _autoResume = MutableStateFlow(p.getBoolean("auto_resume", true))
    val autoResume: StateFlow<Boolean> = _autoResume
    fun setAutoResume(v: Boolean) { p.edit().putBoolean("auto_resume", v).apply(); _autoResume.value = v }
}
