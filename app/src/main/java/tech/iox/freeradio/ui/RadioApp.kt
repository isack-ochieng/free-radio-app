package tech.iox.freeradio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.VolumeDown
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tech.iox.freeradio.core.RadioFrequency
import tech.iox.freeradio.core.RadioStation
import tech.iox.freeradio.ui.components.FrequencyDial
import tech.iox.freeradio.ui.theme.RadioAccent
import tech.iox.freeradio.ui.theme.RadioBackground
import tech.iox.freeradio.ui.theme.RadioMuted
import tech.iox.freeradio.ui.theme.RadioSurface
import tech.iox.freeradio.ui.theme.RadioSurfaceRaised
import tech.iox.freeradio.ui.theme.RadioText
import tech.iox.freeradio.ui.theme.RadioWarning
import tech.iox.freeradio.viewmodel.RadioUiState
import tech.iox.freeradio.viewmodel.RadioViewModel
import java.util.Locale

private enum class AppTab(val label: String) {
    RADIO("Radio"),
    FAVOURITES("Saved"),
    SCAN("Scan"),
    SETTINGS("Settings")
}

@Composable
fun RadioApp(viewModel: RadioViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableStateOf(AppTab.RADIO) }

    Scaffold(
        containerColor = RadioBackground,
        bottomBar = {
            NavigationBar(containerColor = RadioSurface, contentColor = RadioText) {
                AppTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = {
                            Icon(
                                when (tab) {
                                    AppTab.RADIO -> Icons.Outlined.Radio
                                    AppTab.FAVOURITES -> Icons.Outlined.FavoriteBorder
                                    AppTab.SCAN -> Icons.Outlined.Search
                                    AppTab.SETTINGS -> Icons.Outlined.Settings
                                },
                                contentDescription = tab.label
                            )
                        },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { insets ->
        Column(
            Modifier.fillMaxSize().background(RadioBackground).padding(insets)
        ) {
            AppHeader()
            state.notice?.let {
                NoticeBanner(it, viewModel::dismissNotice)
            }
            when (selectedTab) {
                AppTab.RADIO -> RadioHomeScreen(
                    state = state,
                    onPlay = viewModel::togglePlayback,
                    onStep = viewModel::tuneStep,
                    onTune = viewModel::tuneTo,
                    onFavourite = viewModel::toggleFavourite,
                    onScan = { selectedTab = AppTab.SCAN },
                    onOpenFavourites = { selectedTab = AppTab.FAVOURITES },
                    onVolume = viewModel::setVolume
                )
                AppTab.FAVOURITES -> FavouritesScreen(
                    stations = state.favourites,
                    currentFrequency = state.frequencyMhz,
                    onSelect = {
                        viewModel.selectFavourite(it)
                        selectedTab = AppTab.RADIO
                    },
                    onRemove = viewModel::removeFavourite
                )
                AppTab.SCAN -> ScannerScreen(state, viewModel::scan)
                AppTab.SETTINGS -> SettingsScreen(state, viewModel::setVolume)
            }
        }
    }
}

@Composable
private fun AppHeader() {
    Row(
        Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 17.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(RadioAccent),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.GraphicEq, null, tint = RadioBackground, modifier = Modifier.size(25.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("FREE RADIO", color = RadioText, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp, fontSize = 16.sp)
                Text("REAL FM. NO STRINGS.", color = RadioMuted, fontSize = 9.sp, letterSpacing = 1.7.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Surface(color = RadioSurfaceRaised, shape = CircleShape) {
            Row(
                Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(RadioAccent))
                Spacer(Modifier.width(7.dp))
                Text("OFFLINE", color = RadioAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
        }
    }
}

@Composable
private fun RadioHomeScreen(
    state: RadioUiState,
    onPlay: () -> Unit,
    onStep: (Int) -> Unit,
    onTune: (Float) -> Unit,
    onFavourite: () -> Unit,
    onScan: () -> Unit,
    onOpenFavourites: () -> Unit,
    onVolume: (Float) -> Unit
) {
    val frequencyKey = String.format(Locale.US, "%.1f", state.frequencyMhz)
    val isFavourite = state.favourites.any { it.key == frequencyKey }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("Your radio. Your rules.", color = RadioText, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Text("Tune into the frequencies around you.", color = RadioMuted, fontSize = 13.sp)
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = RadioSurface),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                Modifier.padding(horizontal = 19.dp, vertical = 21.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("FM TUNER", color = RadioMuted, fontSize = 10.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.Bold)
                    Surface(
                        color = if (state.isPlaying) RadioAccent.copy(alpha = 0.14f) else RadioSurfaceRaised,
                        shape = CircleShape
                    ) {
                        Text(
                            if (state.isPlaying) "ON AIR" else "STANDBY",
                            color = if (state.isPlaying) RadioAccent else RadioMuted,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                Spacer(Modifier.height(11.dp))
                Text(
                    String.format(Locale.US, "%.1f", state.frequencyMhz),
                    color = RadioText,
                    fontSize = 58.sp,
                    lineHeight = 64.sp,
                    fontWeight = FontWeight.Light,
                    fontFamily = FontFamily.Monospace
                )
                Text("MEGAHERTZ", color = RadioAccent, fontSize = 9.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Bold)
                FrequencyDial(state.frequencyMhz)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("87.5", color = RadioMuted, fontSize = 10.sp)
                    Text("FM BAND", color = RadioMuted, fontSize = 10.sp, letterSpacing = 1.6.sp)
                    Text("108.0", color = RadioMuted, fontSize = 10.sp)
                }
                Slider(
                    value = RadioFrequency.fraction(state.frequencyMhz),
                    onValueChange = { value ->
                        onTune(RadioFrequency.MIN_MHZ + value * (RadioFrequency.MAX_MHZ - RadioFrequency.MIN_MHZ))
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = RadioAccent,
                        activeTrackColor = RadioAccent,
                        inactiveTrackColor = RadioSurfaceRaised
                    )
                )

                Row(
                    Modifier.fillMaxWidth().padding(top = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { onStep(-1) }) {
                        Icon(Icons.Outlined.SkipPrevious, "Previous frequency", tint = RadioText, modifier = Modifier.size(28.dp))
                    }
                    Surface(
                        Modifier.size(72.dp),
                        shape = CircleShape,
                        color = RadioAccent
                    ) {
                        Box(Modifier.fillMaxSize().clickable(onClick = onPlay), contentAlignment = Alignment.Center) {
                            Icon(
                                if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                if (state.isPlaying) "Pause radio" else "Play radio",
                                tint = RadioBackground,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }
                    IconButton(onClick = { onStep(1) }) {
                        Icon(Icons.Outlined.SkipNext, "Next frequency", tint = RadioText, modifier = Modifier.size(28.dp))
                    }
                }

                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("CURRENT PRESET", color = RadioMuted, fontSize = 9.sp, letterSpacing = 1.sp)
                        Text(state.currentStationName, color = RadioText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    IconButton(onClick = onFavourite) {
                        Icon(
                            if (isFavourite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            if (isFavourite) "Remove favourite" else "Save favourite",
                            tint = if (isFavourite) RadioAccent else RadioMuted,
                            modifier = Modifier.size(25.dp)
                        )
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionTile(
                Modifier.weight(1f),
                "Find stations",
                "Scan the FM band",
                { Icon(Icons.Outlined.Search, null, tint = RadioAccent) },
                onScan
            )
            ActionTile(
                Modifier.weight(1f),
                "My favourites",
                state.favourites.size.toString() + " saved",
                { Icon(Icons.Outlined.FavoriteBorder, null, tint = RadioAccent) },
                onOpenFavourites
            )
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = RadioSurface),
            shape = RoundedCornerShape(21.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.VolumeUp, null, tint = RadioMuted)
                        Spacer(Modifier.width(9.dp))
                        Text("Output volume", color = RadioText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text((state.volume * 100).toInt().toString() + "%", color = RadioAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { onVolume((state.volume - 0.05f).coerceAtLeast(0f)) }
                    ) {
                        Icon(
                            Icons.Outlined.VolumeDown,
                            contentDescription = "Decrease volume",
                            tint = RadioMuted
                        )
                    }
                    Slider(
                        value = state.volume,
                        onValueChange = onVolume,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = RadioAccent,
                            activeTrackColor = RadioAccent,
                            inactiveTrackColor = RadioSurfaceRaised
                        )
                    )
                    IconButton(
                        onClick = { onVolume((state.volume + 0.05f).coerceAtMost(1f)) }
                    ) {
                        Icon(
                            Icons.Outlined.VolumeUp,
                            contentDescription = "Increase volume",
                            tint = RadioMuted
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (state.audioRoute.bluetoothOutputConnected) Icons.Outlined.Bluetooth else Icons.Outlined.Headphones,
                        null,
                        tint = RadioMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(state.audioRoute.outputLabel, color = RadioMuted, fontSize = 11.sp)
                }
            }
        }

        if (state.audioRoute.wiredHeadsetConnected) {
            InfoPanel(
                "Wired connection detected",
                "This cable may serve as the FM antenna on compatible phones. Actual antenna support depends on the phone's FM hardware.",
                RadioAccent
            )
        }
        if (state.audioRoute.bluetoothOutputConnected) {
            InfoPanel(
                "Bluetooth audio",
                "Bluetooth can carry the radio sound. It cannot act as the FM antenna.",
                RadioWarning
            )
        }
        BuiltByFooter()
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ActionTile(
    modifier: Modifier,
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = RadioSurface),
        shape = RoundedCornerShape(19.dp)
    ) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(RadioSurfaceRaised),
                contentAlignment = Alignment.Center
            ) { icon() }
            Spacer(Modifier.width(9.dp))
            Column {
                Text(title, color = RadioText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = RadioMuted, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun FavouritesScreen(
    stations: List<RadioStation>,
    currentFrequency: Float,
    onSelect: (RadioStation) -> Unit,
    onRemove: (RadioStation) -> Unit
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ScreenHeading("Your stations", "Frequencies you've chosen to keep.")
        if (stations.isEmpty()) {
            EmptyState(
                "Nothing saved yet",
                "Tune to a frequency and tap the heart. Your station list stays on this phone."
            )
        } else {
            stations.forEach { station ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = RadioSurface),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.fillMaxWidth().clickable { onSelect(station) }.padding(15.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(43.dp).clip(RoundedCornerShape(13.dp)).background(RadioSurfaceRaised),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Outlined.Radio, null, tint = RadioAccent) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(station.name, color = RadioText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text(
                                String.format(Locale.US, "%.1f MHz", station.frequencyMhz),
                                color = RadioMuted,
                                fontSize = 11.sp
                            )
                        }
                        if (station.frequencyMhz == currentFrequency) {
                            Text("TUNED", color = RadioAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = { onRemove(station) }) {
                            Icon(Icons.Outlined.FavoriteBorder, "Remove " + station.name, tint = RadioMuted)
                        }
                    }
                }
            }
        }
        BuiltByFooter()
    }
}

@Composable
private fun ScannerScreen(state: RadioUiState, onScan: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ScreenHeading("Station scanner", "Discover broadcasts in your area.")
        Card(
            colors = CardDefaults.cardColors(containerColor = RadioSurface),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                Modifier.fillMaxWidth().padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier.size(82.dp).clip(CircleShape).background(RadioSurfaceRaised),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.GraphicEq, null, tint = RadioAccent, modifier = Modifier.size(37.dp))
                }
                Spacer(Modifier.height(17.dp))
                Text("Scan the FM band", color = RadioText, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(7.dp))
                Text(
                    "Seek available over-the-air stations and save the frequencies you want to keep.",
                    color = RadioMuted,
                    fontSize = 12.sp,
                    lineHeight = 19.sp
                )
                Spacer(Modifier.height(19.dp))
                Button(
                    onClick = onScan,
                    enabled = !state.isScanning,
                    colors = ButtonDefaults.buttonColors(containerColor = RadioAccent, contentColor = RadioBackground),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (state.isScanning) "SCANNING..." else "START SCAN", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            }
        }
        InfoPanel(
            "Hardware check",
            if (state.audioRoute.wiredHeadsetConnected) {
                "A wired headset is connected. It may provide the antenna on compatible devices, but the FM tuner still needs to be exposed by the phone."
            } else {
                "Some phones have an internal antenna; others require a wired headset. Bluetooth is not an FM antenna."
            },
            RadioWarning
        )
        BuiltByFooter()
    }
}

@Composable
private fun SettingsScreen(state: RadioUiState, onVolume: (Float) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ScreenHeading("Settings", "A simple radio, with the controls that matter.")
        SettingCard(
            "Audio output",
            state.audioRoute.outputLabel,
            "Android and the connected device manage audio routing."
        )
        SettingCard(
            "Wired antenna",
            if (state.audioRoute.wiredHeadsetConnected) "Wired headset detected" else "Not detected",
            "A cable is an FM antenna only when the phone routes its tuner input to the headset connector."
        )
        SettingCard(
            "Bluetooth",
            if (state.audioRoute.bluetoothOutputConnected) "Connected for audio" else "Not connected",
            "Bluetooth headphones can play sound, but cannot provide the FM antenna."
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = RadioSurface),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(17.dp)) {
                Text("Media volume", color = RadioText, fontWeight = FontWeight.SemiBold)
                Slider(
                    value = state.volume,
                    onValueChange = onVolume,
                    colors = SliderDefaults.colors(
                        thumbColor = RadioAccent,
                        activeTrackColor = RadioAccent,
                        inactiveTrackColor = RadioSurfaceRaised
                    )
                )
            }
        }
        InfoPanel(
            "Offline by design",
            "No account. No ads. No subscription. No internet radio. Your favourites and settings stay on this device.",
            RadioAccent
        )
        BuiltByFooter()
    }
}

@Composable
private fun ScreenHeading(title: String, subtitle: String) {
    Column(Modifier.padding(top = 10.dp, bottom = 3.dp)) {
        Text(title, color = RadioText, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        Text(subtitle, color = RadioMuted, fontSize = 13.sp)
    }
}

@Composable
private fun EmptyState(title: String, body: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = RadioSurface),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.fillMaxWidth().padding(25.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Outlined.FavoriteBorder, null, tint = RadioAccent, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(12.dp))
            Text(title, color = RadioText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(6.dp))
            Text(body, color = RadioMuted, fontSize = 12.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun InfoPanel(
    title: String,
    body: String,
    accent: androidx.compose.ui.graphics.Color
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp))
            .background(RadioSurfaceRaised.copy(alpha = 0.72f)).padding(15.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(Icons.Outlined.Info, null, tint = accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = RadioText, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            Text(body, color = RadioMuted, fontSize = 11.sp, lineHeight = 17.sp)
        }
    }
}

@Composable
private fun SettingCard(title: String, value: String, detail: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = RadioSurface),
        shape = RoundedCornerShape(19.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title.uppercase(), color = RadioMuted, fontSize = 9.sp, letterSpacing = 1.3.sp, fontWeight = FontWeight.Bold)
            Text(value, color = RadioText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = RadioMuted, fontSize = 11.sp, lineHeight = 17.sp)
        }
    }
}

@Composable
private fun NoticeBanner(message: String, onDismiss: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(14.dp)).background(RadioWarning.copy(alpha = 0.12f))
            .clickable(onClick = onDismiss).padding(13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.Info, null, tint = RadioWarning, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(9.dp))
        Text(message, color = RadioText, fontSize = 11.sp, lineHeight = 16.sp, modifier = Modifier.weight(1f))
        Text("OK", color = RadioWarning, fontWeight = FontWeight.Bold, fontSize = 10.sp)
    }
}

@Composable
private fun BuiltByFooter() {
    Column(
        Modifier.fillMaxWidth().padding(top = 17.dp, bottom = 15.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("BUILT BY", color = RadioMuted, fontSize = 8.sp, letterSpacing = 2.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            "IOX TECHNOLOGIES",
            color = RadioAccent,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 2.sp
        )
    }
}
