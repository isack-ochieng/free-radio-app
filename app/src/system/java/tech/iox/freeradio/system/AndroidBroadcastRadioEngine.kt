package tech.iox.freeradio.system

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.radio.ProgramSelector
import android.hardware.radio.RadioManager
import android.hardware.radio.RadioTuner
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import tech.iox.freeradio.core.RadioEngine
import tech.iox.freeradio.core.RadioFrequency
import tech.iox.freeradio.core.RadioStation
import java.util.Locale
import kotlin.math.roundToInt

/**
 * System-flavor adapter for Android's Broadcast Radio framework.
 *
 * Compiled only into the privileged system flavor. It requires
 * ACCESS_BROADCAST_RADIO and an enabled FM module in the device radio HAL.
 */
@Suppress("DEPRECATION")
class AndroidBroadcastRadioEngine(context: Context) : RadioEngine {
    private data class FmModule(
        val id: Int,
        val band: RadioManager.FmBandDescriptor
    )

    private val appContext = context.applicationContext
    private val hasPermission = appContext.checkSelfPermission(
        "android.permission.ACCESS_BROADCAST_RADIO"
    ) == PackageManager.PERMISSION_GRANTED

    private val manager: RadioManager? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appContext.getSystemService(RadioManager::class.java)
        } else {
            null
        }
    }.getOrNull()

    private val fmModule: FmModule? = if (hasPermission && manager != null) {
        runCatching { discoverFmModule(manager) }.getOrNull()
    } else {
        null
    }

    override val isSupported: Boolean
        get() = fmModule != null

    override val unavailableReason: String
        get() = when {
            !hasPermission ->
                "Broadcast Radio permission is not granted. Install the system build as a privileged app with the supplied permission allowlist, then reboot."
            manager == null ->
                "Android's Broadcast Radio service is missing on this firmware."
            fmModule == null ->
                "This firmware did not expose an FM Broadcast Radio module. A device-specific vendor FM adapter is required."
            else -> ""
        }

    @Volatile
    private var tuner: RadioTuner? = null

    @Volatile
    private var currentFrequencyKHz = 98_400

    @Volatile
    private var pendingFrequencyKHz: Int? = null

    @Volatile
    private var pendingProgramInfo: CompletableDeferred<RadioManager.ProgramInfo?>? = null

    private val tunerLock = Any()
    private val callbackHandler = Handler(Looper.getMainLooper())

    private val tunerCallback = object : RadioTuner.Callback() {
        override fun onProgramInfoChanged(info: RadioManager.ProgramInfo) {
            if (pendingFrequencyKHz == info.channel) {
                pendingProgramInfo?.complete(info)
            }
        }

        override fun onTuneFailed(result: Int, selector: ProgramSelector?) {
            pendingProgramInfo?.complete(null)
        }

        override fun onError(status: Int) {
            pendingProgramInfo?.complete(null)
        }
    }

    override suspend fun start(): Boolean = withContext(Dispatchers.IO) {
        val activeTuner = ensureTuner() ?: return@withContext false
        runCatching {
            activeTuner.setMute(false) == RadioManager.STATUS_OK
        }.getOrDefault(false)
    }

    override suspend fun pause() = withContext(Dispatchers.IO) {
        val activeTuner = synchronized(tunerLock) {
            val current = tuner
            tuner = null
            current
        } ?: return@withContext

        runCatching { activeTuner.setMute(true) }
        runCatching { activeTuner.close() }
    }

    override suspend fun tune(frequencyMhz: Float): Boolean = withContext(Dispatchers.IO) {
        val channelKHz = (frequencyMhz * 1000f).roundToInt()
        val minimum = (RadioFrequency.MIN_MHZ * 1000).toInt()
        val maximum = (RadioFrequency.MAX_MHZ * 1000).toInt()
        if (channelKHz !in minimum..maximum) return@withContext false

        val activeTuner = ensureTuner() ?: return@withContext false
        val status = runCatching { activeTuner.tune(channelKHz, 0) }
            .getOrDefault(RadioManager.STATUS_ERROR)

        if (status == RadioManager.STATUS_OK) currentFrequencyKHz = channelKHz
        status == RadioManager.STATUS_OK
    }

    override suspend fun scan(): List<RadioStation> = withContext(Dispatchers.IO) {
        val module = fmModule ?: return@withContext emptyList()
        val activeTuner = ensureTuner() ?: return@withContext emptyList()

        val wasMuted = runCatching { activeTuner.getMute() }.getOrDefault(true)
        runCatching { activeTuner.setMute(true) }

        val low = maxOf(module.band.lowerLimit, (RadioFrequency.MIN_MHZ * 1000).toInt())
        val high = minOf(module.band.upperLimit, (RadioFrequency.MAX_MHZ * 1000).toInt())
        val spacing = module.band.spacing.takeIf { it > 0 } ?: 100
        val stations = linkedMapOf<Int, RadioStation>()

        try {
            var channel = low
            while (channel <= high) {
                val reply = CompletableDeferred<RadioManager.ProgramInfo?>()
                pendingFrequencyKHz = channel
                pendingProgramInfo = reply

                val status = runCatching { activeTuner.tune(channel, 0) }
                    .getOrDefault(RadioManager.STATUS_ERROR)

                val info = if (status == RadioManager.STATUS_OK) {
                    withTimeoutOrNull(SCAN_CALLBACK_TIMEOUT_MS) { reply.await() }
                } else {
                    null
                }

                pendingProgramInfo = null
                pendingFrequencyKHz = null

                if (info != null && info.isTuned) {
                    val actualChannel = info.channel
                    if (actualChannel in low..high) {
                        val frequency = actualChannel / 1000f
                        stations[actualChannel] = RadioStation(
                            frequencyMhz = RadioFrequency.normalize(frequency),
                            name = String.format(Locale.US, "FM %.1f MHz", frequency)
                        )
                    }
                }

                channel += spacing
            }
        } finally {
            pendingProgramInfo = null
            pendingFrequencyKHz = null
            runCatching { activeTuner.tune(currentFrequencyKHz, 0) }
            delay(100)
            if (!wasMuted) runCatching { activeTuner.setMute(false) }
        }

        stations.values.sortedBy { it.frequencyMhz }
    }

    private fun ensureTuner(): RadioTuner? {
        tuner?.let { return it }
        val module = fmModule ?: return null
        val radioManager = manager ?: return null

        synchronized(tunerLock) {
            tuner?.let { return it }
            val config = RadioManager.FmBandConfig.Builder(module.band)
                .setStereo(module.band.isStereoSupported)
                .setRds(module.band.isRdsSupported)
                .build()

            val opened = runCatching {
                radioManager.openTuner(
                    module.id,
                    config,
                    true,
                    tunerCallback,
                    callbackHandler
                )
            }.getOrNull()

            tuner = opened
            return opened
        }
    }

    private fun discoverFmModule(radioManager: RadioManager): FmModule? {
        val modules = mutableListOf<RadioManager.ModuleProperties>()
        if (radioManager.listModules(modules) != RadioManager.STATUS_OK) return null

        return modules.firstNotNullOfOrNull { module ->
            if (module.classId != RadioManager.CLASS_AM_FM) {
                return@firstNotNullOfOrNull null
            }

            val fmBand = module.bands
                .filterIsInstance<RadioManager.FmBandDescriptor>()
                .firstOrNull { it.type == RadioManager.BAND_FM }

            fmBand?.let { FmModule(module.id, it) }
        }
    }

    private companion object {
        const val SCAN_CALLBACK_TIMEOUT_MS = 220L
    }
}
