package tech.iox.freeradio.ui

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tech.iox.freeradio.R
import tech.iox.freeradio.core.RadioFrequency
import tech.iox.freeradio.core.RadioStation
import tech.iox.freeradio.reminders.ShowReminder
import tech.iox.freeradio.reminders.ShowReminderScheduler
import tech.iox.freeradio.ui.components.FrequencyDial
import tech.iox.freeradio.ui.theme.*
import tech.iox.freeradio.viewmodel.RadioUiState
import tech.iox.freeradio.viewmodel.RadioViewModel
import java.util.Calendar
import java.util.Locale

private enum class AppTab(val label: String) {
    RADIO("Radio"), SAVED("Saved"), REMINDERS("Reminders"), SCAN("Scan"), SETTINGS("Settings")
}

@Composable
fun RadioApp(viewModel: RadioViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableStateOf(AppTab.RADIO) }
    var showReminderForm by remember { mutableStateOf(false) }
    var editingReminder by remember { mutableStateOf<ShowReminder?>(null) }
    var pendingReminder by remember { mutableStateOf<ShowReminder?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        pendingReminder?.let(viewModel::saveReminder)
        pendingReminder = null
        if (!granted) viewModel.dismissNotice()
    }

    fun saveReminder(reminder: ShowReminder) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            pendingReminder = reminder
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.saveReminder(reminder)
        }
    }

    BackHandler(
        enabled = state.notice != null || showReminderForm || selectedTab != AppTab.RADIO
    ) {
        when {
            state.notice != null -> viewModel.dismissNotice()
            showReminderForm -> {
                showReminderForm = false
                editingReminder = null
            }
            selectedTab != AppTab.RADIO -> selectedTab = AppTab.RADIO
        }
    }

    if (showReminderForm) {
        ReminderEditorDialog(
            existing = editingReminder,
            currentFrequency = state.frequencyMhz,
            onDismiss = {
                showReminderForm = false
                editingReminder = null
            },
            onSave = {
                saveReminder(it)
                showReminderForm = false
                editingReminder = null
            }
        )
    }

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
                                    AppTab.SAVED -> Icons.Outlined.FavoriteBorder
                                    AppTab.REMINDERS -> Icons.Outlined.Alarm
                                    AppTab.SCAN -> Icons.Outlined.Search
                                    AppTab.SETTINGS -> Icons.Outlined.Settings
                                },
                                contentDescription = tab.label
                            )
                        },
                        label = { Text(tab.label, fontSize = 10.sp) }
                    )
                }
            }
        }
    ) { insets ->
        Column(Modifier.fillMaxSize().background(RadioBackground).padding(insets)) {
            AppHeader()
            state.notice?.let { NoticeBanner(it, viewModel::dismissNotice) }
            when (selectedTab) {
                AppTab.RADIO -> RadioHomeScreen(
                    state, viewModel::togglePlayback, viewModel::tuneStep, viewModel::tuneTo,
                    viewModel::toggleFavourite, { selectedTab = AppTab.SCAN },
                    { selectedTab = AppTab.SAVED }, viewModel::setVolume
                )
                AppTab.SAVED -> FavouritesScreen(
                    state.favourites, state.frequencyMhz,
                    onSelect = { viewModel.selectFavourite(it); selectedTab = AppTab.RADIO },
                    onRemove = viewModel::removeFavourite
                )
                AppTab.REMINDERS -> RemindersScreen(
                    state,
                    onAdd = { editingReminder = null; showReminderForm = true },
                    onEdit = { editingReminder = it; showReminderForm = true },
                    onToggle = viewModel::toggleReminder,
                    onDelete = viewModel::deleteReminder
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
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 22.dp, top = 16.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.ic_radio_mark), "Radio", Modifier.size(43.dp))
            Spacer(Modifier.width(11.dp))
            Text("RADIO", color = RadioText, fontWeight = FontWeight.Bold, letterSpacing = 2.5.sp,
                fontSize = 18.sp, fontFamily = FontFamily.Serif)
        }
        Text("FM", color = RadioMuted, fontSize = 11.sp, letterSpacing = 2.sp)
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
    onOpenSaved: () -> Unit,
    onVolume: (Float) -> Unit
) {
    val favouriteKey = String.format(Locale.US, "%.1f", state.frequencyMhz)
    val isFavourite = state.favourites.any { it.key == favouriteKey }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = RadioSurface),
            shape = RoundedCornerShape(27.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(horizontal = 19.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("FM TUNER", color = RadioMuted, fontSize = 10.sp, letterSpacing = 1.8.sp,
                        fontWeight = FontWeight.Bold)
                    Surface(
                        color = if (state.isPlaying) RadioAccent.copy(alpha = .14f) else RadioSurfaceRaised,
                        shape = CircleShape
                    ) {
                        Text(if (state.isPlaying) "ON AIR" else "STANDBY",
                            color = if (state.isPlaying) RadioAccent else RadioMuted,
                            fontSize = 9.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                }
                Spacer(Modifier.height(11.dp))
                Text(String.format(Locale.US, "%.1f", state.frequencyMhz),
                    color = RadioText, fontSize = 58.sp, lineHeight = 64.sp,
                    fontWeight = FontWeight.Normal, fontFamily = FontFamily.Serif)
                Text("MEGAHERTZ", color = RadioAccent, fontSize = 9.sp,
                    letterSpacing = 3.sp, fontWeight = FontWeight.Bold)
                FrequencyDial(state.frequencyMhz)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("87.5", color = RadioMuted, fontSize = 10.sp)
                    Text("FM BAND", color = RadioMuted, fontSize = 10.sp, letterSpacing = 1.6.sp)
                    Text("108.0", color = RadioMuted, fontSize = 10.sp)
                }
                Slider(
                    value = RadioFrequency.fraction(state.frequencyMhz),
                    onValueChange = { onTune(RadioFrequency.MIN_MHZ +
                        it * (RadioFrequency.MAX_MHZ - RadioFrequency.MIN_MHZ)) },
                    colors = SliderDefaults.colors(
                        thumbColor = RadioAccent, activeTrackColor = RadioAccent,
                        inactiveTrackColor = RadioSurfaceRaised
                    )
                )
                Row(Modifier.fillMaxWidth().padding(top = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onStep(-1) }) {
                        Icon(Icons.Outlined.SkipPrevious, "Previous frequency", tint = RadioText,
                            modifier = Modifier.size(28.dp))
                    }
                    Surface(Modifier.size(72.dp), shape = CircleShape, color = RadioAccent) {
                        Box(Modifier.fillMaxSize().clickable(onClick = onPlay), contentAlignment = Alignment.Center) {
                            Icon(if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                if (state.isPlaying) "Pause radio" else "Play radio",
                                tint = RadioBackground, modifier = Modifier.size(34.dp))
                        }
                    }
                    IconButton(onClick = { onStep(1) }) {
                        Icon(Icons.Outlined.SkipNext, "Next frequency", tint = RadioText,
                            modifier = Modifier.size(28.dp))
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 11.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("CURRENT PRESET", color = RadioMuted, fontSize = 9.sp, letterSpacing = 1.sp)
                        Text(state.currentStationName, color = RadioText, fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold)
                    }
                    IconButton(onClick = onFavourite) {
                        Icon(if (isFavourite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            if (isFavourite) "Remove favourite" else "Save favourite",
                            tint = if (isFavourite) RadioAccent else RadioMuted,
                            modifier = Modifier.size(25.dp))
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            ActionTile(Modifier.weight(1f), "Find stations", "Scan FM band",
                { Icon(Icons.Outlined.Search, null, tint = RadioAccent) }, onScan)
            ActionTile(Modifier.weight(1f), "Saved stations", "${state.favourites.size} saved",
                { Icon(Icons.Outlined.FavoriteBorder, null, tint = RadioAccent) }, onOpenSaved)
        }

        Card(colors = CardDefaults.cardColors(containerColor = RadioSurface),
            shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(15.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.VolumeUp, null, tint = RadioMuted)
                        Spacer(Modifier.width(8.dp))
                        Text("Volume", color = RadioText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text("${(state.volume * 100).toInt()}%", color = RadioAccent, fontSize = 12.sp,
                        fontWeight = FontWeight.Bold)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onVolume((state.volume - .05f).coerceAtLeast(0f)) }) {
                        Icon(Icons.Outlined.VolumeDown, "Volume down", tint = RadioMuted)
                    }
                    Slider(value = state.volume, onValueChange = onVolume, modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(thumbColor = RadioAccent,
                            activeTrackColor = RadioAccent, inactiveTrackColor = RadioSurfaceRaised))
                    IconButton(onClick = { onVolume((state.volume + .05f).coerceAtMost(1f)) }) {
                        Icon(Icons.Outlined.VolumeUp, "Volume up", tint = RadioMuted)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (state.audioRoute.bluetoothOutputConnected) Icons.Outlined.Bluetooth
                        else Icons.Outlined.Headphones, null, tint = RadioMuted, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(state.audioRoute.outputLabel, color = RadioMuted, fontSize = 11.sp)
                }
            }
        }

        if (state.audioRoute.wiredHeadsetConnected) {
            InfoPanel("Wired connection detected",
                "The cable may act as the FM antenna on phones wired for headset reception.", RadioAccent)
        }
        if (state.audioRoute.bluetoothOutputConnected) {
            InfoPanel("Bluetooth audio",
                "Bluetooth carries sound but cannot act as the FM antenna.", RadioWarning)
        }
        IsackFooter()
        Spacer(Modifier.height(5.dp))
    }
}

@Composable
private fun ActionTile(
    modifier: Modifier, title: String, subtitle: String,
    icon: @Composable () -> Unit, onClick: () -> Unit
) {
    Card(modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = RadioSurface),
        shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(35.dp).clip(RoundedCornerShape(11.dp)).background(RadioSurfaceRaised),
                contentAlignment = Alignment.Center) { icon() }
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
    stations: List<RadioStation>, currentFrequency: Float,
    onSelect: (RadioStation) -> Unit, onRemove: (RadioStation) -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScreenHeading("Saved stations")
        if (stations.isEmpty()) {
            EmptyState("No saved frequencies", "Tune to a frequency and tap the heart to save it.")
        } else {
            stations.forEach { station ->
                Card(colors = CardDefaults.cardColors(containerColor = RadioSurface),
                    shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().clickable { onSelect(station) }.padding(15.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Image(painterResource(R.drawable.ic_radio_mark), null, Modifier.size(42.dp))
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(station.name, color = RadioText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text(String.format(Locale.US, "%.1f MHz", station.frequencyMhz),
                                color = RadioMuted, fontSize = 11.sp)
                        }
                        if (station.frequencyMhz == currentFrequency) {
                            Text("TUNED", color = RadioAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = { onRemove(station) }) {
                            Icon(Icons.Outlined.DeleteOutline, "Remove ${station.name}", tint = RadioMuted)
                        }
                    }
                }
            }
        }
        IsackFooter()
    }
}

@Composable
private fun RemindersScreen(
    state: RadioUiState,
    onAdd: () -> Unit,
    onEdit: (ShowReminder) -> Unit,
    onToggle: (ShowReminder) -> Unit,
    onDelete: (ShowReminder) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val exactAllowed = ShowReminderScheduler.canScheduleExact(context)
    val notificationsAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            ScreenHeading("Show reminders")
            Button(onClick = onAdd,
                colors = ButtonDefaults.buttonColors(containerColor = RadioAccent, contentColor = RadioBackground),
                shape = RoundedCornerShape(13.dp)) {
                Icon(Icons.Outlined.Alarm, null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(5.dp))
                Text("Add", fontWeight = FontWeight.Bold)
            }
        }

        if (state.reminders.isEmpty()) {
            EmptyState("No reminders yet",
                "Save a show, choose its frequency and weekly time, and Radio will ring when it starts.")
        } else {
            state.reminders.forEach { reminder ->
                ReminderCard(reminder, { onEdit(reminder) }, { onToggle(reminder) }, { onDelete(reminder) })
            }
        }

        if (!exactAllowed) {
            InfoPanel("Exact alarm access is off",
                "Reminders use an approximate schedule until Alarms & reminders access is allowed.",
                RadioWarning)
            OutlinedButton(onClick = {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.parse("package:${context.packageName}")
                    })
                }
            }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp)) {
                Text("ALLOW EXACT REMINDERS")
            }
        }

        if (!notificationsAllowed) {
            InfoPanel("Notifications are disabled",
                "Allow Radio notifications to show reminder alerts and snooze/dismiss controls.",
                RadioWarning)
            OutlinedButton(onClick = {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(
                        Settings.EXTRA_APP_PACKAGE, context.packageName))
                }
            }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp)) {
                Text("OPEN NOTIFICATION SETTINGS")
            }
        }
        IsackFooter()
    }
}

@Composable
private fun ReminderCard(
    reminder: ShowReminder, onEdit: () -> Unit, onToggle: () -> Unit, onDelete: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = RadioSurface),
        shape = RoundedCornerShape(19.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(39.dp).clip(RoundedCornerShape(12.dp)).background(RadioSurfaceRaised),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Alarm, null, tint = RadioAccent)
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(reminder.title, color = RadioText, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(String.format(Locale.US, "%.1f MHz", reminder.frequencyMhz),
                        color = RadioMuted, fontSize = 11.sp)
                }
                Switch(checked = reminder.enabled, onCheckedChange = { onToggle() })
            }
            Text("${reminder.dayLabel()} · ${reminder.timeLabel()}",
                color = if (reminder.enabled) RadioAccent else RadioMuted,
                fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Outlined.Edit, "Edit reminder", tint = RadioMuted)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.DeleteOutline, "Delete reminder", tint = RadioMuted)
                }
            }
        }
    }
}

@Composable
private fun ReminderEditorDialog(
    existing: ShowReminder?, currentFrequency: Float,
    onDismiss: () -> Unit, onSave: (ShowReminder) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var title by remember(existing?.id) { mutableStateOf(existing?.title ?: "Pamba Jatugo") }
    var frequencyText by remember(existing?.id, currentFrequency) {
        mutableStateOf(existing?.frequencyMhz?.let { String.format(Locale.US, "%.1f", it) }
            ?: String.format(Locale.US, "%.1f", currentFrequency))
    }
    var dayOfWeek by remember(existing?.id) { mutableIntStateOf(existing?.dayOfWeek ?: Calendar.FRIDAY) }
    var hour by remember(existing?.id) { mutableIntStateOf(existing?.hour ?: 13) }
    var minute by remember(existing?.id) { mutableIntStateOf(existing?.minute ?: 30) }
    var dayMenuExpanded by remember { mutableStateOf(false) }
    val frequency = frequencyText.toFloatOrNull()
    val valid = title.isNotBlank() && frequency != null && frequency in 87.5f..108.0f

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = RadioSurface,
        title = {
            Text(if (existing == null) "New show reminder" else "Edit reminder",
                color = RadioText, fontFamily = FontFamily.Serif)
        },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(11.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it },
                    label = { Text("Show name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = frequencyText, onValueChange = {
                    frequencyText = it.filter { char -> char.isDigit() || char == '.' }.take(5)
                }, label = { Text("Frequency (MHz)") },
                    supportingText = { Text("FM band: 87.5–108.0 MHz") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Box {
                    OutlinedButton(onClick = { dayMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                        Text(ShowReminder.DAYS.first { it.first == dayOfWeek }.second)
                    }
                    DropdownMenu(expanded = dayMenuExpanded,
                        onDismissRequest = { dayMenuExpanded = false }) {
                        ShowReminder.DAYS.forEach { (day, label) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = {
                                dayOfWeek = day
                                dayMenuExpanded = false
                            })
                        }
                    }
                }
                OutlinedButton(onClick = {
                    TimePickerDialog(context, { _, selectedHour, selectedMinute ->
                        hour = selectedHour
                        minute = selectedMinute
                    }, hour, minute, false).show()
                }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Outlined.Alarm, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(formatTime(hour, minute))
                }
                Text("Repeats every ${ShowReminder.DAYS.first { it.first == dayOfWeek }.second}",
                    color = RadioMuted, fontSize = 11.sp)
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onSave(ShowReminder(
                    id = existing?.id ?: System.currentTimeMillis(),
                    title = title.trim(),
                    frequencyMhz = RadioFrequency.normalize(frequency!!),
                    dayOfWeek = dayOfWeek,
                    hour = hour,
                    minute = minute,
                    enabled = existing?.enabled ?: true
                ))
            }) { Text("SAVE") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CANCEL", color = RadioMuted) }
        }
    )
}

private fun formatTime(hour: Int, minute: Int): String {
    val hour12 = if (hour % 12 == 0) 12 else hour % 12
    return String.format(Locale.US, "%d:%02d %s", hour12, minute, if (hour < 12) "AM" else "PM")
}

@Composable
private fun ScannerScreen(state: RadioUiState, onScan: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)) {
        ScreenHeading("Station scanner")
        Card(colors = CardDefaults.cardColors(containerColor = RadioSurface),
            shape = RoundedCornerShape(23.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Image(painterResource(R.drawable.ic_radio_mark), null, Modifier.size(70.dp))
                Spacer(Modifier.height(15.dp))
                Text("Scan FM band", color = RadioText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(7.dp))
                Text("Search the FM band using Radio's connected hardware tuner.",
                    color = RadioMuted, fontSize = 12.sp, lineHeight = 18.sp)
                Spacer(Modifier.height(17.dp))
                Button(onClick = onScan, enabled = !state.isScanning,
                    colors = ButtonDefaults.buttonColors(containerColor = RadioAccent, contentColor = RadioBackground),
                    shape = RoundedCornerShape(13.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.isScanning) "SCANNING..." else "START SCAN", fontWeight = FontWeight.Bold)
                }
            }
        }
        IsackFooter()
    }
}

@Composable
private fun SettingsScreen(state: RadioUiState, onVolume: (Float) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)) {
        ScreenHeading("Settings")
        SettingCard("Audio output", state.audioRoute.outputLabel)
        SettingCard("Wired headset", if (state.audioRoute.wiredHeadsetConnected) "Connected" else "Not connected")
        SettingCard("Bluetooth", if (state.audioRoute.bluetoothOutputConnected) "Connected" else "Not connected")
        Card(colors = CardDefaults.cardColors(containerColor = RadioSurface),
            shape = RoundedCornerShape(19.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(15.dp)) {
                Text("Media volume", color = RadioText, fontWeight = FontWeight.SemiBold)
                Slider(value = state.volume, onValueChange = onVolume,
                    colors = SliderDefaults.colors(thumbColor = RadioAccent, activeTrackColor = RadioAccent,
                        inactiveTrackColor = RadioSurfaceRaised))
            }
        }
        IsackFooter()
    }
}

@Composable
private fun ScreenHeading(title: String) {
    Text(title, color = RadioText, fontSize = 25.sp, fontWeight = FontWeight.SemiBold,
        fontFamily = FontFamily.Serif, modifier = Modifier.padding(top = 9.dp, bottom = 2.dp))
}

@Composable
private fun EmptyState(title: String, body: String) {
    Card(colors = CardDefaults.cardColors(containerColor = RadioSurface),
        shape = RoundedCornerShape(21.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.Radio, null, tint = RadioAccent, modifier = Modifier.size(30.dp))
            Spacer(Modifier.height(11.dp))
            Text(title, color = RadioText, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Spacer(Modifier.height(6.dp))
            Text(body, color = RadioMuted, fontSize = 12.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun InfoPanel(title: String, body: String, accent: Color) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
        .background(RadioSurfaceRaised.copy(alpha = .72f)).padding(14.dp),
        verticalAlignment = Alignment.Top) {
        Icon(Icons.Outlined.Info, null, tint = accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = RadioText, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            Text(body, color = RadioMuted, fontSize = 11.sp, lineHeight = 17.sp)
        }
    }
}

@Composable
private fun SettingCard(title: String, value: String) {
    Card(colors = CardDefaults.cardColors(containerColor = RadioSurface),
        shape = RoundedCornerShape(17.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title.uppercase(), color = RadioMuted, fontSize = 9.sp,
                letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold)
            Text(value, color = RadioText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun NoticeBanner(message: String, onDismiss: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 5.dp)
        .clip(RoundedCornerShape(13.dp)).background(RadioWarning.copy(alpha = .12f))
        .clickable(onClick = onDismiss).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Info, null, tint = RadioWarning, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(9.dp))
        Text(message, color = RadioText, fontSize = 11.sp, lineHeight = 16.sp,
            modifier = Modifier.weight(1f))
        Text("OK", color = RadioWarning, fontWeight = FontWeight.Bold, fontSize = 10.sp)
    }
}

@Composable
private fun IsackFooter() {
    val context = androidx.compose.ui.platform.LocalContext.current
    Row(
        Modifier.fillMaxWidth().padding(top = 15.dp, bottom = 15.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.footerimage),
            contentDescription = null,
            modifier = Modifier.size(17.dp).clip(CircleShape)
        )
        Spacer(Modifier.width(6.dp))
        Text("Isack Ochieng", color = RadioMuted.copy(alpha = .72f), fontSize = 11.sp,
            fontFamily = FontFamily.Serif, textDecoration = TextDecoration.None,
            modifier = Modifier.clickable {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://isack-ochieng.pages.dev")))
                }
            })
    }
}
