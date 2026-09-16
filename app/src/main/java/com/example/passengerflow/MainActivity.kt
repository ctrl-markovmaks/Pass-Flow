package com.example.passengerflow

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.passengerflow.data.AppDatabase
import com.example.passengerflow.data.MeasurementEntity
import com.example.passengerflow.data.StopEntity
import com.example.passengerflow.location.LocationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.view.HapticFeedbackConstants
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.filled.Share
import androidx.core.content.FileProvider
import android.content.ClipData
import java.io.File

private const val ROUTE_MEASURE = "measure"
private const val ROUTE_HISTORY = "history"
private const val ROUTE_DETAILS = "details/{id}"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PassengerFlowApp()
        }
    }
}

fun strongVibration(context: Context) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager =
            context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        manager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(
            VibrationEffect.createOneShot(
                80L,
                255
            )
        )
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(80L)
    }
}

fun shareCsv(
    context: Context,
    measurementName: String,
    csv: String
) {
    val fileName = "${measurementName.ifBlank { "measurement" }}.csv"

    val exportDir = File(context.cacheDir, "exports").apply {
        mkdirs()
    }

    val file = File(exportDir, fileName)
    file.writeText(csv, Charsets.UTF_8)

    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, measurementName)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = ClipData.newRawUri("CSV", uri)
    }

    context.startActivity(
        Intent.createChooser(intent, "Экспорт измерения")
    )
}

class PassengerFlowViewModel(
    context: Context,
    private val db: AppDatabase,
    private val locationRepository: LocationRepository
) : ViewModel() {
    val measurements = db.measurementDao().observeMeasurements()

    val currentTime = MutableStateFlow(System.currentTimeMillis())
    var autoLocation by mutableStateOf(true)
        private set
    var currentLatitude by mutableStateOf<Double?>(null)
        private set
    var currentLongitude by mutableStateOf<Double?>(null)
        private set
    var locationStatus by mutableStateOf("Координаты не получены")
        private set
    var isLoadingLocation by mutableStateOf(false)
        private set
    var sessionActive by mutableStateOf(false)
        private set
    var arrivalTime by mutableStateOf<Long?>(null)
        private set
    var entered by mutableIntStateOf(0)
        private set
    var exited by mutableIntStateOf(0)
        private set
    var stops by mutableStateOf<List<StopDraft>>(emptyList())
        private set

    init {
        viewModelScope.launch {
            while (true) {
                currentTime.value = System.currentTimeMillis()
                kotlinx.coroutines.delay(1000)
            }
        }
    }

    fun updateAutoLocation(value: Boolean) { autoLocation = value }
    fun incrementEntered() {
        entered++
    }

    fun decrementEntered() {
        if (entered > 0) entered--
    }

    fun incrementExited() {
        exited++
    }

    fun decrementExited() {
        if (exited > 0) exited--
    }

    fun csvValue(value: String): String {
        return "\"${value.replace("\"", "\"\"")}\""
    }

    suspend fun requestLocation(): Boolean {
        isLoadingLocation = true
        return try {
            val location = locationRepository.getCurrentLocation()
            if (location != null) {
                currentLatitude = location.latitude
                currentLongitude = location.longitude
                locationStatus = "${location.latitude.formatCoord()}, ${location.longitude.formatCoord()}"
                true
            } else {
                locationStatus = "GPS не вернул координаты"
                false
            }
        } catch (e: Exception) {
            locationStatus = "Не удалось получить GPS"
            false
        } finally {
            isLoadingLocation = false
        }
    }

    suspend fun startMeasurement(): Boolean {
        sessionActive = true
        arrivalTime = System.currentTimeMillis()
        entered = 0
        exited = 0
        if (autoLocation) requestLocation()
        return true
    }

    fun setLastStopName(name: String) {
        if (stops.isEmpty()) return

        val lastIndex = stops.lastIndex
        val trimmedName = name.trim()

        val finalName = trimmedName.ifBlank {
            formatCoords(
                stops[lastIndex].latitude,
                stops[lastIndex].longitude
            )
        }

        val updatedStops = stops.toMutableList()
        updatedStops[lastIndex] = updatedStops[lastIndex].copy(
            name = finalName
        )

        stops = updatedStops
    }

    fun resetCounters() { entered = 0; exited = 0 }

    fun arrive(scope: kotlinx.coroutines.CoroutineScope, onDone: () -> Unit) {
        scope.launch {
            startMeasurement()
            onDone()
        }
    }

    fun depart() {
        val arrival = arrivalTime ?: return
        stops = stops + StopDraft(
            name = null,
            latitude = currentLatitude,
            longitude = currentLongitude,
            arrivalTimeMillis = arrival,
            departureTimeMillis = System.currentTimeMillis(),
            entered = entered,
            exited = exited
        )
        arrivalTime = null
        resetCounters()
    }

    suspend fun saveSession(name: String) {
        val sessionName = name.trim().ifBlank { "Измерение ${formatDateTime(System.currentTimeMillis())}" }
        val measurementId = db.measurementDao().insertMeasurement(
            MeasurementEntity(name = sessionName, createdAtMillis = System.currentTimeMillis())
        )
        db.measurementDao().insertStops(
            stops.map {
                StopEntity(
                    measurementId = measurementId,
                    name = it.name,
                    latitude = it.latitude,
                    longitude = it.longitude,
                    arrivalTimeMillis = it.arrivalTimeMillis,
                    departureTimeMillis = it.departureTimeMillis,
                    entered = it.entered,
                    exited = it.exited
                )
            }
        )
        clearSession()
    }

    fun clearSession() {
        sessionActive = false
        arrivalTime = null
        entered = 0
        exited = 0
        stops = emptyList()
        currentLatitude = null
        currentLongitude = null
        locationStatus = "Координаты не получены"
    }

    fun observeStops(id: Long): Flow<List<StopEntity>> = db.measurementDao().observeStops(id)

    suspend fun findMeasurement(id: Long): MeasurementEntity? = db.measurementDao().findMeasurement(id)

    suspend fun buildCsv(measurementId: Long): String {
        val measurement = db.measurementDao().findMeasurement(measurementId)
            ?: return ""

        val stops = db.measurementDao().getStops(measurementId)

        return buildString {
            appendLine("Название измерения;Дата создания")
            appendLine(
                "${csvValue(measurement.name)};" +
                        "${csvValue(formatDateTime(measurement.createdAtMillis))}"
            )

            appendLine()
            appendLine(
                "№;Остановка;Широта;Долгота;Дата;Время прибытия;Время отправления;Вошло;Вышло"
            )

            stops.forEachIndexed { index, stop ->
                val stopName = stop.name
                    ?.takeIf { it.isNotBlank() }
                    ?: formatCoords(stop.latitude, stop.longitude)

                appendLine(
                    "${index + 1};" +
                            "${csvValue(stopName)};" +
                            "${csvValue(stop.latitude?.toString() ?: "")};" +
                            "${csvValue(stop.longitude?.toString() ?: "")};" +
                            "${csvValue(formatDate(stop.arrivalTimeMillis))};" +
                            "${csvValue(formatTime(stop.arrivalTimeMillis))};" +
                            "${csvValue(stop.departureTimeMillis?.let(::formatTime) ?: "")};" +
                            "${stop.entered};" +
                            "${stop.exited}"
                )
            }
        }
    }
}

data class StopDraft(
    val name: String?,
    val latitude: Double?,
    val longitude: Double?,
    val arrivalTimeMillis: Long,
    val departureTimeMillis: Long,
    val entered: Int,
    val exited: Int
)

class PassengerFlowVmFactory(
    private val context: Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val db = AppDatabase.get(context)
        @Suppress("UNCHECKED_CAST")
        return PassengerFlowViewModel(context, db, LocationRepository(context)) as T
    }
}

@Composable
fun PassengerFlowApp() {
    val context = LocalContext.current
    val vm: PassengerFlowViewModel = viewModel(factory = PassengerFlowVmFactory(context))
    val navController = rememberNavController()

    MaterialTheme {
        NavHost(navController = navController, startDestination = ROUTE_MEASURE) {
            composable(ROUTE_MEASURE) {
                MeasurementScreen(vm, navController)
            }
            composable(ROUTE_HISTORY) {
                HistoryScreen(vm, navController)
            }
            composable(ROUTE_DETAILS) { backStackEntry ->
                val id = backStackEntry.arguments?.getString("id")?.toLongOrNull() ?: return@composable
                DetailsScreen(vm, id, navController)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeasurementScreen(vm: PassengerFlowViewModel, nav: NavHostController) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var pendingArrival by remember { mutableStateOf(false) }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            if (pendingArrival) {
                pendingArrival = false
                vm.arrive(scope) {}
            } else {
                scope.launch { vm.requestLocation() }
            }
        } else {
            pendingArrival = false
        }
    }
    var showNameDialog by remember { mutableStateOf(false) }
    var showStopNameDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Обследование пассажиропотока") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(modifier = Modifier.weight(1f), onClick = { nav.navigate(ROUTE_HISTORY) }) {
                    Text("История")
                }
                Button(
                    modifier = Modifier.weight(1f),
                    enabled = vm.stops.isNotEmpty() && vm.arrivalTime == null,
                    onClick = { showNameDialog = true }
                ) {
                    Text("Завершить обследование")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Системное время", style = MaterialTheme.typography.labelLarge)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccessTime, contentDescription = null)
                        Spacer(Modifier.width(10.dp))
                        val currentTime by vm.currentTime.collectAsStateWithLifecycle()
                        Text(formatClock(millis = currentTime), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    }
                    Text("Последнее GPS: ${vm.locationStatus}", style = MaterialTheme.typography.bodyMedium)
                }
            }

            Button(
                modifier = Modifier.fillMaxWidth().height(68.dp),
                onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    ) {
                        vm.arrive(scope) {}
                    } else {
                        pendingArrival = true
                        locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    }
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    strongVibration(context)
                },
                enabled = vm.arrivalTime == null
            ) {
                Icon(Icons.Default.LocationOn, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (vm.stops.isEmpty() && !vm.sessionActive) "Прибытие" else "Прибытие на остановку")
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CounterCard(
                    title = "Вошло",
                    value = vm.entered,
                    onIncrement = {
                        vm.incrementEntered()
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        strongVibration(context)
                    },
                    onDecrement = {
                        vm.decrementEntered()
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        strongVibration(context)
                    },
                    enabled = vm.arrivalTime != null
                )
                CounterCard(
                    title = "Вышло",
                    value = vm.exited,
                    onIncrement = {
                        vm.incrementExited()
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        strongVibration(context)
                    },
                    onDecrement = {
                        vm.decrementExited()
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        strongVibration(context)
                    },
                    enabled = vm.arrivalTime != null
                )
            }

            Button(
                modifier = Modifier.fillMaxWidth().height(54.dp),
                enabled = vm.arrivalTime != null,
                onClick = {
                    vm.depart()
                    showStopNameDialog = true
                }
            ) {
                Icon(Icons.Default.Send, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Отправление")
            }

            Card {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text("Автоопределение GPS при «Прибытии»", fontWeight = FontWeight.SemiBold)
                            Text("Координаты записываются в блок остановки", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = vm.autoLocation, onCheckedChange = vm::updateAutoLocation)
                    }
                    OutlinedButton(
                        onClick = {
                            val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                            if (hasPermission) scope.launch { vm.requestLocation() }
                            else locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                        },
                        enabled = !vm.isLoadingLocation
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (vm.isLoadingLocation) "Определяем…" else "Запросить геолокацию")
                    }
                }
            }

            if (vm.stops.isNotEmpty()) {
                Text("Завершенные остановки: ${vm.stops.size}", style = MaterialTheme.typography.titleMedium)
                vm.stops.forEachIndexed { index, stop ->
                    CompactStopCard(index + 1, stop)
                }
            }

            Spacer(Modifier.height(80.dp))
        }
    }

    if (showNameDialog) {
        NameDialog(
            onDismiss = { showNameDialog = false },
            onSave = { name ->
                scope.launch {
                    vm.saveSession(name)
                    showNameDialog = false
                    nav.navigate(ROUTE_HISTORY) {
                        popUpTo(ROUTE_MEASURE) { inclusive = true }
                    }
                }
            }
        )
    }
    if (showStopNameDialog) {
        StopNameDialog(
            onDismiss = {
                vm.setLastStopName("")
                showStopNameDialog = false
            },
            onSave = { name ->
                vm.setLastStopName(name)
                showStopNameDialog = false
            }
        )
    }
}


@Composable
fun CounterCard(
    title: String,
    value: Int,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    enabled: Boolean
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge
            )

            Text(
                value.toString(),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onDecrement,
                    enabled = enabled && value > 0,
                    modifier = Modifier.size(68.dp)
                ) {
                    Text(
                        "−1",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(
                    onClick = onIncrement,
                    enabled = enabled,
                    modifier = Modifier.size(68.dp)
                ) {
                    Text(
                        "+1",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun CompactStopCard(number: Int, stop: StopDraft) {
    Card {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stop.name?.takeIf { it.isNotBlank() } ?: formatCoords(stop.latitude, stop.longitude),
                fontWeight = FontWeight.SemiBold
            )
            Text("Прибытие: ${formatDateTime(stop.arrivalTimeMillis)}")
            Text("Отправление: ${formatDateTime(stop.departureTimeMillis)}")
            Text("Вошло: ${stop.entered} • Вышло: ${stop.exited}")
            Text("GPS: ${formatCoords(stop.latitude, stop.longitude)}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(vm: PassengerFlowViewModel, nav: NavHostController) {
    val items by vm.measurements.collectAsState(initial = emptyList())
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("История измерений") },
            navigationIcon = { IconButton(onClick = { nav.navigateUp() }) { Icon(Icons.Default.ArrowBack, contentDescription = "Назад") } }
        )
    }) { padding ->
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Сохраненных измерений пока нет")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(items, key = { it.id }) { measurement ->
                    HistoryCard(measurement) { nav.navigate("details/${measurement.id}") }
                }
            }
        }
    }
}

@Composable
fun HistoryCard(measurement: MeasurementEntity, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(measurement.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
            Text(formatDateTime(measurement.createdAtMillis), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(vm: PassengerFlowViewModel, id: Long, nav: NavHostController) {
    val stops by vm.observeStops(id).collectAsState(initial = emptyList())
    val measurement by produceMeasurement(vm, id)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(measurement?.name ?: "Детали") },
            navigationIcon = {
                IconButton(onClick = { nav.navigateUp() }) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Назад"
                    )
                }
            },
            actions = {
                IconButton(
                    onClick = {
                        val currentMeasurement = measurement ?: return@IconButton

                        scope.launch {
                            val csv = vm.buildCsv(id)

                            shareCsv(
                                context = context,
                                measurementName = currentMeasurement.name,
                                csv = csv
                            )
                        }
                    }
                ) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = "Экспорт CSV"
                    )
                }
            }
        )
    }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Остановка", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Text("Прибытие", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Text("Отправление", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Text("Вошло", Modifier.width(54.dp), fontWeight = FontWeight.Bold)
                    Text("Вышло", Modifier.width(54.dp), fontWeight = FontWeight.Bold)
                }
                Divider(Modifier.padding(vertical = 8.dp))
            }
            items(stops, key = { it.id }) { stop ->
                StopDetailRow(stop)
            }
        }
    }
}

@Composable
fun StopDetailRow(stop: StopEntity) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stop.name?.takeIf { it.isNotBlank() }
                    ?: formatCoords(stop.latitude, stop.longitude),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = { openMap(context, stop.latitude, stop.longitude) },
                enabled = stop.latitude != null && stop.longitude != null
            ) { Icon(Icons.Default.Map, contentDescription = "Открыть на карте") }
        }
        Text(formatTime(stop.arrivalTimeMillis), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        Text(stop.departureTimeMillis?.let(::formatTime) ?: "—", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        Text(stop.entered.toString(), Modifier.width(54.dp))
        Text(stop.exited.toString(), Modifier.width(54.dp))
    }
}

@Composable
fun produceMeasurement(vm: PassengerFlowViewModel, id: Long): androidx.compose.runtime.State<MeasurementEntity?> {
    val state = remember { mutableStateOf<MeasurementEntity?>(null) }
    LaunchedEffect(id) {
        state.value = vm.findMeasurement(id)
    }
    return state
}

@Composable
fun NameDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Название измерения") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("Например: Маршрут 24, утренний пик") }
            )
        },
        confirmButton = { Button(onClick = { onSave(name) }) { Text("Сохранить") } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
fun StopNameDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Название остановки")
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = {
                    Text("Например: Центральная")
                }
            )
        },
        confirmButton = {
            Button(
                onClick = { onSave(name) }
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss
            ) {
                Text("Пропустить")
            }
        }
    )
}

fun openMap(context: Context, latitude: Double?, longitude: Double?) {
    if (latitude == null || longitude == null) return
    val uri = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude")
    val intent = Intent(Intent.ACTION_VIEW, uri)
    try { context.startActivity(intent) } catch (_: ActivityNotFoundException) {
        val fallback = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=$latitude,$longitude"))
        context.startActivity(fallback)
    }
}

private fun Double.formatCoord(): String = String.format(Locale.US, "%.6f", this)
private fun formatCoords(lat: Double?, lon: Double?): String =
    if (lat != null && lon != null) "${lat.formatCoord()}, ${lon.formatCoord()}" else "—"
private fun formatClock(millis: Long) = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(millis))
private fun formatTime(millis: Long) = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(millis))
private fun formatDateTime(millis: Long) = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date(millis))
private fun formatDate(millis: Long): String {
    return SimpleDateFormat(
        "dd.MM.yyyy",
        Locale.getDefault()
    ).format(Date(millis))
}