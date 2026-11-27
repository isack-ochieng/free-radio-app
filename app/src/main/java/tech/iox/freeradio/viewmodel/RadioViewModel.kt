package tech.iox.freeradio.viewmodel

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tech.iox.freeradio.core.AudioRouteMonitor
import tech.iox.freeradio.core.AudioRouteState
import tech.iox.freeradio.core.RadioEngineProvider
import tech.iox.freeradio.core.RadioFrequency
import tech.iox.freeradio.core.RadioStation
import tech.iox.freeradio.core.StationStore
import tech.iox.freeradio.core.VolumeController
import tech.iox.freeradio.playback.RadioPlaybackService
import java.util.Locale

data class RadioUiState(
    val frequencyMhz: Float = 98.4f,
    val isPlaying: Boolean = false,
    val isScanning: Boolean = false,
    val favourites: List<RadioStation> = emptyList(),
    val audioRoute: AudioRouteState = AudioRouteState(),
    val volume: Float = 0.5f,
    val notice: String? = null
) {
    val currentStationName: String
        get() = favourites.firstOrNull {
            it.key == String.format(Locale.US, "%.1f", frequencyMhz)
        }?.name ?: "Manual frequency"
}

class RadioViewModel(application: Application) : AndroidViewModel(application) {
    private val stationStore = StationStore(application)
    private val radioEngine = RadioEngineProvider.get(application)
    private val volumeController = VolumeController(application)
    private val routeMonitor = AudioRouteMonitor(application) { route ->
        _uiState.update { it.copy(audioRoute = route) }
    }

    private val _uiState = MutableStateFlow(
        RadioUiState(volume = volumeController.currentLevel())
    )
    val uiState: StateFlow<RadioUiState> = _uiState.asStateFlow()

    init {
        routeMonitor.start()
        viewModelScope.launch {
            stationStore.favourites.collect { favourites ->
                _uiState.update { it.copy(favourites = favourites) }
            }
        }
    }

    fun tuneStep(direction: Int) {
        tuneTo(RadioFrequency.step(_uiState.value.frequencyMhz, direction))
    }

    fun tuneTo(value: Float) {
        val frequency = RadioFrequency.normalize(value)
        _uiState.update { it.copy(frequencyMhz = frequency, notice = null) }
        getApplication<Application>()
            .getSharedPreferences("radio_playback_state", 0)
            .edit()
            .putFloat("last_frequency", frequency)
            .apply()

        if (radioEngine.isSupported) {
            viewModelScope.launch { radioEngine.tune(frequency) }
        }
    }

    fun toggleFavourite() {
        val frequency = _uiState.value.frequencyMhz
        val key = String.format(Locale.US, "%.1f", frequency)
        val existing = _uiState.value.favourites.firstOrNull { it.key == key }

        viewModelScope.launch {
            if (existing != null) {
                stationStore.remove(existing)
            } else {
                stationStore.save(
                    RadioStation(
                        frequencyMhz = frequency,
                        name = "FM " + String.format(Locale.US, "%.1f", frequency)
                    )
                )
            }
        }
    }

    fun selectFavourite(station: RadioStation) = tuneTo(station.frequencyMhz)

    fun removeFavourite(station: RadioStation) {
        viewModelScope.launch { stationStore.remove(station) }
    }

    fun scan() {
        if (!radioEngine.isSupported) {
            showNotice(
                "This phone has not exposed an FM receiver to Free Radio. " +
                    "Scanning will be enabled when a compatible tuner adapter is available."
            )
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, notice = null) }
            runCatching { radioEngine.scan() }
                .onSuccess { found ->
                    found.forEach { stationStore.save(it) }
                    _uiState.update {
                        it.copy(
                            isScanning = false,
                            notice = if (found.isEmpty()) {
                                "No stations were found in this scan."
                            } else {
                                "Found " + found.size + " station(s). Saved locally."
                            }
                        )
                    }
                }
                .onFailure {
                    _uiState.update {
                        it.copy(isScanning = false, notice = "The FM scan could not be completed.")
                    }
                }
        }
    }

    fun togglePlayback() {
        val app = getApplication<Application>()
        if (_uiState.value.isPlaying) {
            app.startService(
                Intent(app, RadioPlaybackService::class.java).apply {
                    action = RadioPlaybackService.ACTION_PAUSE
                }
            )
            _uiState.update { it.copy(isPlaying = false) }
            return
        }

        if (!radioEngine.isSupported) {
            showNotice(
                "FM hardware is not available to this app on this phone. " +
                    "Your saved frequencies remain available locally."
            )
            return
        }

        val intent = Intent(app, RadioPlaybackService::class.java).apply {
            action = RadioPlaybackService.ACTION_PLAY
            putExtra(RadioPlaybackService.EXTRA_FREQUENCY, _uiState.value.frequencyMhz)
        }
        ContextCompat.startForegroundService(app, intent)
        _uiState.update { it.copy(isPlaying = true, notice = null) }
    }

    fun setVolume(level: Float) {
        volumeController.setLevel(level)
        _uiState.update { it.copy(volume = volumeController.currentLevel()) }
    }

    fun dismissNotice() {
        _uiState.update { it.copy(notice = null) }
    }

    private fun showNotice(message: String) {
        _uiState.update { it.copy(notice = message) }
    }

    override fun onCleared() {
        routeMonitor.stop()
        super.onCleared()
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (!modelClass.isAssignableFrom(RadioViewModel::class.java)) {
                throw IllegalArgumentException("Unknown ViewModel class")
            }
            return RadioViewModel(application) as T
        }
    }
}
