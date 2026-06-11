@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.anime4k.upscaler

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Anime4KApp() }
    }
}

data class ClassicSettings(
    val scale: Float = 2f,
    val iterations: Float = 3f,
    val pcs: Float = 0f,
    val pgs: Float = 1f,
    val parallelJobs: Float = 2f,
    val outputFps: Float = 0f,
    val fpsMode: String = "keep",
    val crf: Float = 18f,
    val encoderPreset: String = "veryfast",
    val deleteFrames: Boolean = true,
    val keepWork: Boolean = false,
    val pauseEvery: Float = 0f,
    val pauseSeconds: Float = 10f,
)

enum class AppScreen(val label: String) {
    Photo("Фото"),
    Video("Видео"),
    Saved("Сохранённые"),
    Settings("Настройки"),
}

data class FfmpegProgress(
    val percent: Float,
    val currentSizeBytes: Long,
    val estimatedSizeBytes: Long,
    val etaSeconds: Long?,
    val speed: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Anime4KApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("anime4k_settings", Context.MODE_PRIVATE) }
    var showSliders by remember { mutableStateOf(prefs.getBoolean("show_sliders", true)) }
    var keepScreenOn by remember { mutableStateOf(prefs.getBoolean("keep_screen_on", true)) }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF1A73E8),
            onPrimary = Color.White,
            secondary = Color(0xFF34A853),
            background = Color(0xFFF8FAFD),
            surface = Color.White,
            surfaceVariant = Color(0xFFE8F0FE),
            onSurface = Color(0xFF202124),
            onSurfaceVariant = Color(0xFF5F6368),
        ),
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp),
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(28.dp),
            extraLarge = RoundedCornerShape(32.dp),
        ),
    ) {
        val scope = rememberCoroutineScope()
        var screen by remember { mutableStateOf(AppScreen.Photo) }
        var settings by remember { mutableStateOf(ClassicSettings()) }
        var inputUri by remember { mutableStateOf<Uri?>(null) }
        var outputFile by remember { mutableStateOf<File?>(null) }
        var outputIsVideo by remember { mutableStateOf(false) }
        var status by remember { mutableStateOf("Выберите файл для апскейла") }
        var progress by remember { mutableStateOf(0f) }
        var processing by remember { mutableStateOf(false) }
        var savedFiles by remember { mutableStateOf(listSavedResults(context)) }

        val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
        LaunchedEffect(Unit) {
            if (Build.VERSION.SDK_INT >= 33) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            inputUri = uri
            outputFile = null
            outputIsVideo = false
            status = if (uri != null) "Фото выбрано" else "Выбор отменён"
            progress = 0f
        }
        val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            inputUri = uri
            outputFile = null
            outputIsVideo = true
            status = if (uri != null) "Видео выбрано" else "Выбор отменён"
            progress = 0f
        }

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Anime4K", fontWeight = FontWeight.Bold)
                            Text("Classic Rust Upscaler", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                )
            },
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    AppScreen.entries.forEach { item ->
                        NavigationBarItem(
                            selected = screen == item,
                            onClick = {
                                screen = item
                                if (item == AppScreen.Saved) savedFiles = listSavedResults(context)
                            },
                            icon = { Text(screenIcon(item), style = MaterialTheme.typography.titleMedium) },
                            label = { Text(item.label) },
                        )
                    }
                }
            },
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                when (screen) {
                    AppScreen.Photo -> ProcessingScreen(
                        isVideo = false,
                        settings = settings,
                        showSliders = showSliders,
                        inputUri = inputUri,
                        outputFile = outputFile,
                        outputIsVideo = outputIsVideo,
                        status = status,
                        progress = progress,
                        processing = processing,
                        onPick = { photoPicker.launch("image/*") },
                        onRun = {
                            val uri = inputUri ?: return@ProcessingScreen
                            scope.launch {
                                processing = true
                                progress = 0f
                                outputFile = null
                                if (keepScreenOn) startProcessingForeground(context, "Подготовка фото")
                                try {
                                    val file = processPhoto(context, uri, settings) { text, p ->
                                        status = text
                                        progress = p
                                        if (keepScreenOn) updateProcessingNotification(context, text, p)
                                    }
                                    outputFile = file
                                    outputIsVideo = false
                                    savedFiles = listSavedResults(context)
                                    status = "Готово: ${file.name}"
                                    progress = 1f
                                } catch (e: Throwable) {
                                    status = "Ошибка: ${e.message}"
                                } finally {
                                    if (keepScreenOn) stopProcessingForeground(context)
                                    processing = false
                                }
                            }
                        },
                        onSettings = { settings = it },
                    )
                    AppScreen.Video -> ProcessingScreen(
                        isVideo = true,
                        settings = settings,
                        showSliders = showSliders,
                        inputUri = inputUri,
                        outputFile = outputFile,
                        outputIsVideo = outputIsVideo,
                        status = status,
                        progress = progress,
                        processing = processing,
                        onPick = { videoPicker.launch("video/*") },
                        onRun = {
                            val uri = inputUri ?: return@ProcessingScreen
                            scope.launch {
                                processing = true
                                progress = 0f
                                outputFile = null
                                if (keepScreenOn) startProcessingForeground(context, "Подготовка видео")
                                try {
                                    val file = processVideo(context, uri, settings) { text, p ->
                                        status = text
                                        progress = p
                                        if (keepScreenOn) updateProcessingNotification(context, text, p)
                                    }
                                    outputFile = file
                                    outputIsVideo = true
                                    savedFiles = listSavedResults(context)
                                    status = "Готово: ${file.name}"
                                    progress = 1f
                                } catch (e: Throwable) {
                                    status = "Ошибка: ${e.message}"
                                } finally {
                                    if (keepScreenOn) stopProcessingForeground(context)
                                    processing = false
                                }
                            }
                        },
                        onSettings = { settings = it },
                    )
                    AppScreen.Saved -> SavedScreen(
                        files = savedFiles,
                        onRefresh = { savedFiles = listSavedResults(context) },
                        onDelete = { file ->
                            file.delete()
                            savedFiles = listSavedResults(context)
                        },
                    )
                    AppScreen.Settings -> AppSettingsScreen(
                        showSliders = showSliders,
                        keepScreenOn = keepScreenOn,
                        onShowSliders = {
                            showSliders = it
                            prefs.edit().putBoolean("show_sliders", it).apply()
                        },
                        onKeepScreenOn = {
                            keepScreenOn = it
                            prefs.edit().putBoolean("keep_screen_on", it).apply()
                        },
                        onRequestNotifications = {
                            if (Build.VERSION.SDK_INT >= 33) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        },
                    )
                }
            }
        }
    }
}

fun screenIcon(screen: AppScreen): String = when (screen) {
    AppScreen.Photo -> "🖼️"
    AppScreen.Video -> "🎬"
    AppScreen.Saved -> "📁"
    AppScreen.Settings -> "⚙️"
}

@Composable
fun ProcessingScreen(
    isVideo: Boolean,
    settings: ClassicSettings,
    showSliders: Boolean,
    inputUri: Uri?,
    outputFile: File?,
    outputIsVideo: Boolean,
    status: String,
    progress: Float,
    processing: Boolean,
    onPick: () -> Unit,
    onRun: () -> Unit,
    onSettings: (ClassicSettings) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            HeroCard(isVideo = isVideo, processing = processing, status = status, progress = progress, onPick = onPick, onRun = onRun, inputUri = inputUri)
        }
        if (!isVideo) {
            item { ImagePreview("Вход", inputUri) }
            outputFile?.let { item { ImagePreview("Результат", Uri.fromFile(it)) } }
        } else {
            item { VideoInfoCard(inputUri, outputFile) }
        }
        outputFile?.let { file ->
            item { ResultActions(file, outputIsVideo, onDeleted = {}) }
        }
        item { SettingsCard(settings, isVideo, showSliders, onSettings) }
    }
}

@Composable
fun HeroCard(isVideo: Boolean, processing: Boolean, status: String, progress: Float, onPick: () -> Unit, onRun: () -> Unit, inputUri: Uri?) {
    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(if (isVideo) "Улучшение видео" else "Улучшение фото", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                if (isVideo) "Видео разбирается на PNG-кадры, кадры улучшаются классическим Rust Anime4K-rs, затем собирается MP4."
                else "Фото улучшается тем же классическим Rust Anime4K-rs ядром.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onPick, enabled = !processing) { Text(if (isVideo) "Выбрать видео" else "Выбрать фото") }
                FilledTonalButton(onClick = onRun, enabled = inputUri != null && !processing) { Text(if (processing) "Работает" else "Запустить") }
            }
            if (processing || progress > 0f) LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            ProgressStatusCard(status)
        }
    }
}

@Composable
fun ProgressStatusCard(status: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(18.dp),
    ) {
        Text(
            text = status,
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun VideoInfoCard(inputUri: Uri?, outputFile: File?) {
    ElevatedCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Видео", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(inputUri?.toString() ?: "Видео не выбрано", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            outputFile?.let { Text("Результат: ${it.name}", color = MaterialTheme.colorScheme.primary) }
        }
    }
}

@Composable
fun ImagePreview(title: String, uri: Uri?) {
    if (uri == null) return
    ElevatedCard {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Image(
                painter = rememberAsyncImagePainter(uri),
                contentDescription = title,
                modifier = Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(18.dp)),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

@Composable
fun ResultActions(file: File, isVideo: Boolean, onDeleted: () -> Unit) {
    val context = LocalContext.current
    var message by remember { mutableStateOf("") }
    ElevatedCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(file.name, fontWeight = FontWeight.Bold)
            Text(formatBytes(file.length()), color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    runCatching { saveToGallery(context, file, isVideo) }
                        .onSuccess { message = "Сохранено в галерею" }
                        .onFailure { message = "Ошибка сохранения: ${it.message}" }
                }) { Text("Сохранить") }
                OutlinedButton(onClick = {
                    runCatching { shareFile(context, file, isVideo) }
                        .onFailure { message = "Ошибка: ${it.message}" }
                }) { Text("Поделиться") }
                OutlinedButton(onClick = {
                    file.delete()
                    message = "Удалено из памяти приложения"
                    onDeleted()
                }) { Text("Удалить") }
            }
            if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun SettingsCard(settings: ClassicSettings, isVideo: Boolean, showSliders: Boolean, onChange: (ClassicSettings) -> Unit) {
    ElevatedCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("Параметры Anime4K-rs", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            SectionTitle("Качество кадра")
            NumericSetting("Масштаб", "anime4k -s", settings.scale, 0.5f, 8f, 0.25f, "x", showSliders) { onChange(settings.copy(scale = it)) }
            NumericSetting("Прогоны", "anime4k -i", settings.iterations, 1f, 20f, 1f, "", showSliders) { onChange(settings.copy(iterations = it)) }
            NumericSetting("Push Gradient", "--pgs", settings.pgs, 0f, 10f, 0.05f, "", showSliders) { onChange(settings.copy(pgs = it)) }
            NumericSetting("Push Color", "--pcs", settings.pcs, 0f, 10f, 0.05f, "", showSliders) { onChange(settings.copy(pcs = it)) }
            if (isVideo) {
                SectionTitle("Видео")
                NumericSetting("Параллельные процессы", "Сколько кадров улучшать одновременно", settings.parallelJobs, 1f, 16f, 1f, "", showSliders) { onChange(settings.copy(parallelJobs = it)) }
                NumericSetting("Выходной FPS", "0 = оригинальный FPS", settings.outputFps, 0f, 240f, 1f, " fps", showSliders) { onChange(settings.copy(outputFps = it)) }
                SegmentedSetting("Режим FPS", listOf("keep", "simple", "interpolate"), settings.fpsMode) { onChange(settings.copy(fpsMode = it)) }
                NumericSetting("Качество видео", "CRF для x264 или q:v fallback", settings.crf, 0f, 51f, 1f, "", showSliders) { onChange(settings.copy(crf = it)) }
                SegmentedSetting("Preset", listOf("ultrafast", "superfast", "veryfast", "faster", "fast", "medium", "slow"), settings.encoderPreset) { onChange(settings.copy(encoderPreset = it)) }
                ToggleSetting("Удалять исходные PNG", "Экономит место", settings.deleteFrames) { onChange(settings.copy(deleteFrames = it)) }
                ToggleSetting("Оставить рабочую папку", "Для отладки и продолжения", settings.keepWork) { onChange(settings.copy(keepWork = it)) }
                NumericSetting("Пауза каждые N кадров", "0 = без пауз", settings.pauseEvery, 0f, 1000f, 10f, "", showSliders) { onChange(settings.copy(pauseEvery = it)) }
                NumericSetting("Длительность паузы", "Секунды", settings.pauseSeconds, 0f, 300f, 1f, " сек", showSliders) { onChange(settings.copy(pauseSeconds = it)) }
            }
        }
    }
}

@Composable
fun SavedScreen(files: List<File>, onRefresh: () -> Unit, onDelete: (File) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Сохранённые в приложении", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                TextButton(onClick = onRefresh) { Text("Обновить") }
            }
        }
        if (files.isEmpty()) {
            item { EmptyState("Пока нет результатов", "После обработки файлы будут появляться здесь. Их можно сохранить в галерею, удалить из памяти приложения или поделиться.") }
        } else {
            items(files, key = { it.absolutePath }) { file ->
                val isVideo = file.extension.lowercase() == "mp4"
                ResultActions(file, isVideo, onDeleted = { onDelete(file) })
            }
        }
    }
}

@Composable
fun AppSettingsScreen(
    showSliders: Boolean,
    keepScreenOn: Boolean,
    onShowSliders: (Boolean) -> Unit,
    onKeepScreenOn: (Boolean) -> Unit,
    onRequestNotifications: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Настройки приложения", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        ElevatedCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ToggleSetting("Показывать ползунки", "Если выключить — останется точный ручной ввод чисел.", showSliders, onShowSliders)
                ToggleSetting("Фоновая активность и уведомление", "Во время обработки включается foreground service и постоянное уведомление со статусом.", keepScreenOn, onKeepScreenOn)
                Button(onClick = onRequestNotifications) { Text("Разрешить уведомления") }
                Text("Для фоновой работы добавлен foreground service. Android всё равно может ограничивать долгие задачи, но уведомление показывает, что апскейл живой.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        EmptyState("Стиль Google / Material", "Интерфейс переведён на светлый Material 3: нижняя навигация, карточки, крупные действия и аккуратные поля ввода.")
    }
}

@Composable
fun EmptyState(title: String, description: String) {
    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
}

@Composable
fun ToggleSetting(title: String, description: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SegmentedSetting(title: String, values: List<String>, selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            values.forEach { value ->
                FilterChip(selected = selected == value, onClick = { onSelect(value) }, label = { Text(value) })
            }
        }
    }
}

@Composable
fun NumericSetting(title: String, description: String, value: Float, min: Float, max: Float, step: Float, suffix: String, showSlider: Boolean, onValue: (Float) -> Unit) {
    var text by remember(value) { mutableStateOf(formatNumber(value)) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedTextField(
                value = text,
                onValueChange = { raw ->
                    text = raw.replace(',', '.')
                    text.toFloatOrNull()?.let { onValue(it) }
                },
                singleLine = true,
                modifier = Modifier.width(120.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                suffix = { Text(suffix) },
            )
        }
        if (showSlider) {
            Slider(
                value = value.coerceIn(min, max),
                onValueChange = { raw ->
                    val rounded = ((raw / step).roundToInt() * step).coerceIn(min, max)
                    onValue(rounded)
                    text = formatNumber(rounded)
                },
                valueRange = min..max,
            )
        }
        if (value < min || value > max) Text("Экспертное значение: ${formatNumber(value)}", color = Color(0xFFF9AB00), style = MaterialTheme.typography.bodySmall)
    }
}

suspend fun processPhoto(context: Context, uri: Uri, settings: ClassicSettings, update: (String, Float) -> Unit): File = withContext(Dispatchers.IO) {
    withContext(Dispatchers.Main) { update("Подготовка фото...", 0.05f) }
    val input = copyUriToCache(context, uri, "anime4k_input", ".png")
    val output = File(resultsDir(context), "anime4k_photo_${System.currentTimeMillis()}.png")
    val response = NativeBridge.processClassicImage(
        input.absolutePath,
        output.absolutePath,
        settings.scale.toDouble(),
        settings.iterations.roundToInt().coerceAtLeast(1),
        settings.pcs.toDouble(),
        settings.pgs.toDouble(),
    )
    if (!response.startsWith("OK:")) error(response.removePrefix("ERR:"))
    withContext(Dispatchers.Main) { update("Фото готово", 1f) }
    output
}

suspend fun processVideo(context: Context, uri: Uri, settings: ClassicSettings, update: (String, Float) -> Unit): File = withContext(Dispatchers.IO) {
    val started = System.currentTimeMillis()
    val work = File(context.cacheDir, "anime4k_video_$started")
    val frames = File(work, "frames")
    val upscaled = File(work, "upscaled")
    frames.mkdirs(); upscaled.mkdirs()

    withContext(Dispatchers.Main) { update("Копирование видео...", 0.02f) }
    val input = copyUriToCache(context, uri, "anime4k_video_input", ".mp4")
    val originalFps = probeFps(context, input).ifBlank { "24000/1001" }
    val durationMs = probeDurationMs(context, input).coerceAtLeast(1L)
    val finalFps = if (settings.outputFps > 0f) formatNumber(settings.outputFps) else originalFps

    withContext(Dispatchers.Main) { update("Этап 1/4: разбор видео на PNG-кадры...\nДлительность: ${formatDuration(durationMs / 1000)}", 0.08f) }
    runFfmpegWithProgress(
        context = context,
        args = listOf("-hide_banner", "-y", "-i", input.absolutePath, "-vsync", "0", File(frames, "%08d.png").absolutePath),
        durationMs = durationMs,
        outputFile = null,
        baseProgress = 0.08f,
        progressSpan = 0.10f,
        stage = "Этап 1/4: разбор кадров",
        update = update,
    )

    val frameFiles = frames.listFiles { file -> file.extension.lowercase() == "png" }?.sortedBy { it.name }.orEmpty()
    if (frameFiles.isEmpty()) error("Не удалось извлечь кадры")

    val jobs = settings.parallelJobs.roundToInt().coerceAtLeast(1)
    val semaphore = Semaphore(jobs)
    var done = 0
    val total = frameFiles.size
    val pauseEvery = settings.pauseEvery.roundToInt()
    val pauseMs = (settings.pauseSeconds * 1000).roundToInt().coerceAtLeast(0).toLong()

    val animeStarted = System.currentTimeMillis()
    var lastUpscaledBytes = 0L
    withContext(Dispatchers.Main) { update("Этап 2/4: Anime4K обработка кадров\nКадры: 0 / $total\nОсталось: считаю...", 0.18f) }
    coroutineScope {
        frameFiles.map { frame ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    val out = File(upscaled, frame.name)
                    if (!out.exists() || out.length() == 0L) {
                        val response = NativeBridge.processClassicImage(frame.absolutePath, out.absolutePath, settings.scale.toDouble(), settings.iterations.roundToInt().coerceAtLeast(1), settings.pcs.toDouble(), settings.pgs.toDouble())
                        if (!response.startsWith("OK:")) error(response.removePrefix("ERR:"))
                    }
                    if (settings.deleteFrames) frame.delete()
                    val current = synchronized(work) { done += 1; done }
                    if (pauseEvery > 0 && current % pauseEvery == 0 && pauseMs > 0) Thread.sleep(pauseMs)
                    withContext(Dispatchers.Main) {
                        val p = 0.18f + (current.toFloat() / total.toFloat()) * 0.62f
                        val elapsedSec = ((System.currentTimeMillis() - animeStarted).coerceAtLeast(1L) / 1000.0)
                        val fps = current / elapsedSec
                        val etaSec = if (fps > 0.0) ((total - current) / fps).toLong() else null
                        if (current == total || current % 10 == 0) lastUpscaledBytes = directorySize(upscaled)
                        update(
                            "Этап 2/4: Anime4K обработка кадров\n" +
                                "Кадры: $current / $total (${String.format("%.1f", current * 100.0 / total)}%)\n" +
                                "Скорость: ${String.format("%.2f", fps)} кадр/сек\n" +
                                "Готовые кадры: ${formatBytes(lastUpscaledBytes)}\n" +
                                "Осталось: ${formatEta(etaSec)}",
                            p,
                        )
                    }
                }
            }
        }.awaitAll()
    }

    val output = File(resultsDir(context), "anime4k_video_${System.currentTimeMillis()}.mp4")
    val upscaledBytes = directorySize(upscaled)
    withContext(Dispatchers.Main) { update("Этап 3/4: подготовка сборки MP4\nГотовые кадры: ${formatBytes(upscaledBytes)}", 0.82f) }
    val hasX264 = hasFfmpegEncoder(context, "libx264")
    val command = mutableListOf("-hide_banner", "-y", "-framerate", originalFps, "-i", File(upscaled, "%08d.png").absolutePath, "-i", input.absolutePath, "-map", "0:v:0", "-map", "1:a?")
    if (settings.fpsMode == "simple" && settings.outputFps > 0f) command += listOf("-r", finalFps)
    if (settings.fpsMode == "interpolate" && settings.outputFps > 0f) command += listOf("-vf", "minterpolate=fps=$finalFps:mi_mode=mci:mc_mode=aobmc:me_mode=bidir:vsbmc=1")
    command += if (hasX264) listOf("-c:v", "libx264", "-crf", settings.crf.roundToInt().toString(), "-preset", settings.encoderPreset) else listOf("-c:v", "mpeg4", "-q:v", crfToMpeg4Quality(settings.crf).toString())
    command += listOf("-pix_fmt", "yuv420p", "-c:a", "copy", "-shortest", output.absolutePath)
    runFfmpegWithProgress(
        context = context,
        args = command,
        durationMs = durationMs,
        outputFile = output,
        baseProgress = 0.82f,
        progressSpan = 0.16f,
        stage = "Этап 4/4: сборка MP4",
        update = update,
    )
    if (!settings.keepWork) work.deleteRecursively()
    withContext(Dispatchers.Main) { update("Видео готово", 1f) }
    output
}

fun resultsDir(context: Context): File = File(context.filesDir, "results").apply { mkdirs() }

fun listSavedResults(context: Context): List<File> {
    return resultsDir(context).listFiles { file -> file.isFile && file.length() > 0L && file.extension.lowercase() in setOf("png", "jpg", "jpeg", "mp4") }
        ?.sortedByDescending { it.lastModified() }
        .orEmpty()
}

fun ffmpegPath(context: Context): String = File(context.applicationInfo.nativeLibraryDir, "libffmpeg.so").absolutePath
fun ffprobePath(context: Context): String = File(context.applicationInfo.nativeLibraryDir, "libffprobe.so").absolutePath

fun hasFfmpegEncoder(context: Context, encoder: String): Boolean = runCatching { runProcess(listOf(ffmpegPath(context), "-hide_banner", "-encoders")).contains(encoder) }.getOrDefault(false)

fun crfToMpeg4Quality(crf: Float): Int = when {
    crf <= 12f -> 1
    crf <= 18f -> 2
    crf <= 23f -> 3
    crf <= 28f -> 5
    crf <= 35f -> 8
    else -> 12
}

fun runProcess(args: List<String>, workingDir: File? = null): String {
    val process = ProcessBuilder(args).redirectErrorStream(true).apply { if (workingDir != null) directory(workingDir) }.start()
    val output = process.inputStream.bufferedReader().readText()
    val code = process.waitFor()
    if (code != 0) error(output.ifBlank { "Command failed with code $code" })
    return output
}

fun runFfmpeg(context: Context, args: List<String>) { runProcess(listOf(ffmpegPath(context)) + args) }

suspend fun runFfmpegWithProgress(
    context: Context,
    args: List<String>,
    durationMs: Long,
    outputFile: File?,
    baseProgress: Float,
    progressSpan: Float,
    stage: String,
    update: (String, Float) -> Unit,
) = withContext(Dispatchers.IO) {
    val started = System.currentTimeMillis()
    val processArgs = listOf(ffmpegPath(context), "-progress", "pipe:1", "-nostats") + args
    val process = ProcessBuilder(processArgs).redirectErrorStream(true).start()
    var currentSizeBytes = 0L
    var speed = ""
    process.inputStream.bufferedReader().forEachLine { line ->
        val parsed = parseFfmpegProgressLine(line, durationMs, outputFile, started, currentSizeBytes, speed)
        if (line.startsWith("total_size=")) currentSizeBytes = line.substringAfter("=").toLongOrNull() ?: currentSizeBytes
        if (line.startsWith("speed=")) speed = line.substringAfter("=").trim()
        if (parsed != null) {
            currentSizeBytes = parsed.currentSizeBytes
            speed = parsed.speed.ifBlank { speed }
            val totalText = if (parsed.estimatedSizeBytes > 0L) " / Примерный итог: ~${formatBytes(parsed.estimatedSizeBytes)}" else ""
            val text = stage + "\n" +
                "Готово: ${String.format("%.1f", parsed.percent * 100.0)}%\n" +
                "Файл: ${formatBytes(parsed.currentSizeBytes)}$totalText\n" +
                "Скорость: ${parsed.speed.ifBlank { "—" }}\n" +
                "Осталось: ${formatEta(parsed.etaSeconds)}"
            withContext(Dispatchers.Main) { update(text, baseProgress + parsed.percent * progressSpan) }
        }
    }
    val code = process.waitFor()
    if (code != 0) error("FFmpeg завершился с кодом $code")
}

fun parseFfmpegProgressLine(
    line: String,
    durationMs: Long,
    outputFile: File?,
    started: Long,
    previousSizeBytes: Long,
    previousSpeed: String,
): FfmpegProgress? {
    if (!line.startsWith("out_time_ms=") && !line.startsWith("out_time_us=")) return null
    val timeValue = line.substringAfter("=").toLongOrNull() ?: return null
    val currentMs = timeValue / 1000L
    val percent = (currentMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    val currentSize = outputFile?.takeIf { it.exists() }?.length()?.takeIf { it > 0L } ?: previousSizeBytes
    val estimated = if (percent > 0.05f && currentSize > 0L) (currentSize / percent).toLong() else 0L
    val elapsedSec = ((System.currentTimeMillis() - started).coerceAtLeast(1L) / 1000.0)
    val eta = if (percent > 0.02f) ((elapsedSec * (1.0 - percent) / percent)).toLong() else null
    return FfmpegProgress(percent, currentSize, estimated, eta, previousSpeed)
}

fun probeFps(context: Context, file: File): String {
    val output = runProcess(listOf(ffprobePath(context), "-v", "error", "-select_streams", "v:0", "-show_entries", "stream=r_frame_rate", "-of", "default=nk=1:nw=1", file.absolutePath))
    return output.lineSequence().firstOrNull()?.trim().orEmpty()
}

fun probeDurationMs(context: Context, file: File): Long {
    val output = runProcess(listOf(ffprobePath(context), "-v", "error", "-show_entries", "format=duration", "-of", "default=nk=1:nw=1", file.absolutePath))
    val seconds = output.lineSequence().firstOrNull()?.trim()?.toDoubleOrNull() ?: 0.0
    return (seconds * 1000.0).toLong()
}

fun formatNumber(value: Float): String = if (value % 1f == 0f) value.roundToInt().toString() else String.format("%.2f", value)

fun formatBytes(bytes: Long): String {
    val mb = bytes / 1024.0 / 1024.0
    return if (mb >= 1024) String.format("%.2f ГБ", mb / 1024.0) else String.format("%.1f МБ", mb)
}

fun formatEta(seconds: Long?): String {
    if (seconds == null || seconds < 0) return "считаю..."
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

fun formatDuration(seconds: Long): String = formatEta(seconds)

fun directorySize(dir: File): Long {
    if (!dir.exists()) return 0L
    return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
}

fun copyUriToCache(context: Context, uri: Uri, prefix: String, suffix: String): File {
    val output = File(context.cacheDir, "${prefix}_${System.currentTimeMillis()}$suffix")
    context.contentResolver.openInputStream(uri).use { input ->
        FileOutputStream(output).use { out -> requireNotNull(input) { "Не удалось открыть файл" }.copyTo(out) }
    }
    return output
}

fun saveToGallery(context: Context, file: File, isVideo: Boolean): Uri {
    val resolver = context.contentResolver
    val collection = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
        put(MediaStore.MediaColumns.MIME_TYPE, if (isVideo) "video/mp4" else "image/png")
        put(MediaStore.MediaColumns.RELATIVE_PATH, if (isVideo) "Movies/Anime4K" else "Pictures/Anime4K")
    }
    val uri = resolver.insert(collection, values) ?: error("Не удалось создать MediaStore URI")
    resolver.openOutputStream(uri).use { out -> file.inputStream().use { input -> requireNotNull(out).let { input.copyTo(it) } } }
    return uri
}

fun shareFile(context: Context, file: File, isVideo: Boolean) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = if (isVideo) "video/mp4" else "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Поделиться результатом"))
}
