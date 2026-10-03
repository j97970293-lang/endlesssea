package dev.endlesssea.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.endlesssea.data.db.GenreDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val downloadPathDisplay: String = "/Téléchargements/EndlessSea/",
    val wifiOnly: Boolean = true,
    val partsPerTask: Int = 4,
    val parallelTasks: Int = 2,
    val defaultSpeed: Float = 1f,
    val autoResume: Boolean = true,
    val genres: List<String> = emptyList(),
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val genreDao: GenreDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState

    init {
        viewModelScope.launch {
            genreDao.observeVisible().collect { visible ->
                _uiState.value = _uiState.value.copy(genres = visible.map { it.name })
            }
        }
    }

    fun chooseStorage() {
        // Déclenche ACTION_OPEN_DOCUMENT_TREE côté Activity et persiste l'URI SAF (doc 08 §1).
    }

    fun setWifiOnly(v: Boolean) { _uiState.value = _uiState.value.copy(wifiOnly = v) }
    fun setAutoResume(v: Boolean) { _uiState.value = _uiState.value.copy(autoResume = v) }

    fun toggleGenre(name: String, visible: Boolean) = viewModelScope.launch {
        genreDao.observeAll().first().firstOrNull { it.name == name }?.let {
            genreDao.setVisible(it.id, visible)
        }
    }
}
