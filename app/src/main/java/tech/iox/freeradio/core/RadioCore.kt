package tech.iox.freeradio.core

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

private val Context.radioDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "free_radio_preferences"
)

data class RadioStation(
    val frequencyMhz: Float,
    val name: String
) {
    val key: String
        get() = String.format(java.util.Locale.US, "%.1f", frequencyMhz)
}

object RadioFrequency {
    const val MIN_MHZ = 87.5f
    const val MAX_MHZ = 108.0f
    const val STEP_MHZ = 0.1f

    fun normalize(value: Float): Float {
        val bounded = value.coerceIn(MIN_MHZ, MAX_MHZ)
        return (bounded * 10f).roundToInt() / 10f
    }

    fun step(current: Float, direction: Int): Float =
        normalize(current + direction * STEP_MHZ)

    fun fraction(value: Float): Float =
        ((normalize(value) - MIN_MHZ) / (MAX_MHZ - MIN_MHZ)).coerceIn(0f, 1f)
}

class StationStore(context: Context) {
    private val dataStore = context.applicationContext.radioDataStore
    private val favouritesKey = stringPreferencesKey("favourite_stations")

    val favourites: Flow<List<RadioStation>> = dataStore.data.map { preferences ->
        decode(preferences[favouritesKey].orEmpty())
    }

    suspend fun save(station: RadioStation) {
        dataStore.edit { preferences ->
            val current = decode(preferences[favouritesKey].orEmpty())
            val updated = (current.filterNot { it.key == station.key } + station)
                .sortedBy { it.frequencyMhz }
            preferences[favouritesKey] = encode(updated)
        }
    }

    suspend fun remove(station: RadioStation) {
        dataStore.edit { preferences ->
            val current = decode(preferences[favouritesKey].orEmpty())
            preferences[favouritesKey] = encode(
                current.filterNot { it.key == station.key }
            )
        }
    }

    private fun encode(stations: List<RadioStation>): String {
        val array = JSONArray()
        stations.forEach { station ->
            array.put(
                JSONObject()
                    .put("frequency", station.frequencyMhz.toDouble())
                    .put("name", station.name)
            )
        }
        return array.toString()
    }

    private fun decode(raw: String): List<RadioStation> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val frequency = item.optDouble("frequency", Double.NaN).toFloat()
                    if (frequency.isFinite() &&
                        frequency in RadioFrequency.MIN_MHZ..RadioFrequency.MAX_MHZ
                    ) {
                        add(
                            RadioStation(
                                frequencyMhz = RadioFrequency.normalize(frequency),
                                name = item.optString("name").ifBlank { "FM station" }
                            )
                        )
                    }
                }
            }.distinctBy { it.key }.sortedBy { it.frequencyMhz }
        }.getOrDefault(emptyList())
    }
}

/**
 * An FM receiver adapter. The standard Play variant intentionally has no
 * privileged adapter; the system variant supplies one when Android exposes
 * an FM Broadcast Radio module and grants the required system permission.
 */
interface RadioEngine {
    val isSupported: Boolean
    val unavailableReason: String
        get() = "Direct FM access is unavailable in this installation. The system-integrated build requires privileged Broadcast Radio access and a device FM HAL."
    suspend fun start(): Boolean
    suspend fun pause()
    suspend fun tune(frequencyMhz: Float): Boolean
    suspend fun scan(): List<RadioStation>
}

class UnavailableRadioEngine(
    override val unavailableReason: String =
        "Direct FM access is unavailable in this installation. The system-integrated build requires privileged Broadcast Radio access and a device FM HAL."
) : RadioEngine {
    override val isSupported = false
    override suspend fun start() = false
    override suspend fun pause() = Unit
    override suspend fun tune(frequencyMhz: Float) = false
    override suspend fun scan() = emptyList<RadioStation>()
}

object RadioEngineProvider {
    private const val SYSTEM_ENGINE_CLASS =
        "tech.iox.freeradio.system.AndroidBroadcastRadioEngine"

    @Volatile
    private var instance: RadioEngine? = null

    fun get(context: Context): RadioEngine =
        instance ?: synchronized(this) {
            instance ?: createEngine(context).also { instance = it }
        }

    private fun createEngine(context: Context): RadioEngine = runCatching {
        val engineClass = Class.forName(SYSTEM_ENGINE_CLASS)
        val constructor = engineClass.getDeclaredConstructor(Context::class.java)
        constructor.newInstance(context.applicationContext) as RadioEngine
    }.getOrElse {
        UnavailableRadioEngine()
    }

    fun install(engine: RadioEngine) {
        synchronized(this) {
            instance = engine
        }
    }
}

data class AudioRouteState(
    val wiredHeadsetConnected: Boolean = false,
    val bluetoothOutputConnected: Boolean = false,
    val outputLabel: String = "Phone speaker"
)

class AudioRouteMonitor(
    context: Context,
    private val onChanged: (AudioRouteState) -> Unit
) {
    private val audioManager = context.getSystemService(AudioManager::class.java)

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = refresh()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = refresh()
    }

    fun start() {
        audioManager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        refresh()
    }

    fun stop() {
        audioManager.unregisterAudioDeviceCallback(callback)
    }

    private fun refresh() {
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
        val wired = outputs.any {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }
        val bluetooth = outputs.any {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                (android.os.Build.VERSION.SDK_INT >= 31 &&
                    it.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
        }
        val label = when {
            bluetooth -> "Bluetooth audio connected"
            wired -> "Wired audio connected"
            outputs.any { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER } -> "Phone speaker"
            else -> "Default audio output"
        }
        onChanged(AudioRouteState(wired, bluetooth, label))
    }
}

class VolumeController(context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)

    fun currentLevel(): Float {
        val maximum = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (maximum <= 0) return 0f
        return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maximum
    }

    fun setLevel(level: Float) {
        val maximum = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = (level.coerceIn(0f, 1f) * maximum).roundToInt()
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
    }
}
