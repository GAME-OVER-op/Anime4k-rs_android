package com.anime4k.upscaler

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

enum class MediaTab { Photo, Video }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Anime4KApp() {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF8B5CF6),
            secondary = Color(0xFF22D3EE),
            background = Color(0xFF0F172A),
            surface = Color(0xFF111827),
        )
    ) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var tab by remember { mutableStateOf(MediaTab.Photo) }
        var settings by remember { mutableStateOf(ClassicSettings()) }
        var inputUri by remember { mutableStateOf<Uri?>(null) }
        var outputFile by remember { mutableStateOf<File?>(null) }
        var outputIsVideo by remember { mutableStateOf(false) }
        var status by remember { mutableStateOf("Выберите фото или видео") }
        var progress by remember { mutableStateOf(0f) }
        var processing by remember { mutableStateOf(false) }

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

        Scaffold(topBar = { TopAppBar(title = { Text("Anime4K Апскейлер") }) }) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    "Классический Anime4K-rs: те же реальные параметры, что были в рабочем скрипте — scale, iterations, pgs, pcs, FPS, CRF, preset и параллельная обработка кадров.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFD1D5DB),
                )

                TabRow(selectedTabIndex = if (tab == MediaTab.Photo) 0 else 1) {
                    Tab(selected = tab == MediaTab.Photo, onClick = { tab = MediaTab.Photo }, text = { Text("Фото") })
                    Tab(selected = tab == MediaTab.Video, onClick = { tab = MediaTab.Video }, text = { Text("Видео") })
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = {
                        outputFile = null
                        if (tab == MediaTab.Photo) photoPicker.launch("image/*") else videoPicker.launch("video/*")
                    }) { Text(if (tab == MediaTab.Photo) "Выбрать фото" else "Выбрать видео") }

                    Button(
                        enabled = inputUri != null && !processing,
                        onClick = {
                            val uri = inputUri ?: return@Button
                            scope.launch {
                                processing = true
                                progress = 0f
                                outputFile = null
                                try {
                                    val file = if (tab == MediaTab.Photo) {
                                        outputIsVideo = false
                                        processPhoto(context, uri, settings) { text, p -> status = text; progress = p }
                                    } else {
                                        outputIsVideo = true
                                        processVideo(context, uri, settings) { text, p -> status = text; progress = p }
                                    }
                                    outputFile = file
                                    status = "Готово: ${file.name}"
                                    progress = 1f
                                } catch (e: Throwable) {
                                    status = "Ошибка: ${e.message}"
                                } finally {
                                    processing = false
                                }
                            }
                        }
                    ) { Text(if (processing) "Обработка..." else "Запустить") }
                }

                if (processing || progress > 0f) {
                    LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                }
                Text(status, color = Color(0xFFE5E7EB))

                if (tab == MediaTab.Photo) {
                    ImagePreview("Вход", inputUri)
                    outputFile?.let { ImagePreview("Результат", Uri.fromFile(it)) }
                } else {
                    inputUri?.let { Text("Видео выбрано: $it", color = Color(0xFFD1D5DB), style = MaterialTheme.typography.bodySmall) }
                    outputFile?.let { Text("Результат: ${it.absolutePath}", color = Color(0xFFD1D5DB), style = MaterialTheme.typography.bodySmall) }
                }

                outputFile?.let { file ->
                    ResultActions(file, outputIsVideo)
                }

                SettingsCard(settings, tab) { settings = it }
            }
        }
    }
}

@Composable
fun ImagePreview(title: String, uri: Uri?) {
    if (uri == null) return
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF111827))) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Image(
                painter = rememberAsyncImagePainter(uri),
                contentDescription = title,
                modifier = Modifier.fillMaxWidth().height(220.dp),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

@Composable
fun ResultActions(file: File, isVideo: Boolean) {
    val context = LocalContext.current
    var message by remember { mutableStateOf("") }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = {
            runCatching { saveToGallery(context, file, isVideo) }
                .onSuccess { message = "Сохранено в галерею" }
                .onFailure { message = "Ошибка сохранения: ${it.message}" }
        }) { Text("Сохранить") }
        OutlinedButton(onClick = {
            runCatching { shareFile(context, file, isVideo) }
                .onFailure { message = "Ошибка: ${it.message}" }
        }) { Text("Поделиться") }
    }
    if (message.isNotBlank()) Text(message, color = Color(0xFFE5E7EB))
}

@Composable
fun SettingsCard(settings: ClassicSettings, tab: MediaTab, onChange: (ClassicSettings) -> Unit) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF111827))) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("Настройки Anime4K-rs Classic", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "Здесь только реальные параметры старого Rust-ядра и старого video script. У чисел есть ползунок и ручной ввод без жёсткого ограничения.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF9CA3AF),
            )

            SectionTitle("Качество кадра")
            NumericSliderSetting("Масштаб", "anime4k -s. Для 1080p→4K обычно 2. Можно ввести больше вручную.", settings.scale, 0.5f, 8f, 0.25f, "x") { onChange(settings.copy(scale = it)) }
            NumericSliderSetting("Количество прогонов", "anime4k -i. В старом скрипте хорошее значение было 3.", settings.iterations, 1f, 20f, 1f, "") { onChange(settings.copy(iterations = it)) }
            NumericSliderSetting("Push Gradient", "--pgs. Главная сила восстановления контуров. Обычно 1.", settings.pgs, 0f, 10f, 0.05f, "") { onChange(settings.copy(pgs = it)) }
            NumericSliderSetting("Push Color", "--pcs. Подтягивание цвета. Обычно 0.", settings.pcs, 0f, 10f, 0.05f, "") { onChange(settings.copy(pcs = it)) }

            if (tab == MediaTab.Video) {
                SectionTitle("Видео")
                NumericSliderSetting("Одновременных upscale-процессов", "Сколько кадров улучшать одновременно. 1 = как старый скрипт. 2–4 = быстрее на мощном устройстве.", settings.parallelJobs, 1f, 16f, 1f, "") { onChange(settings.copy(parallelJobs = it)) }
                NumericSliderSetting("Выходной FPS", "0 = оставить FPS оригинала. Иначе simple/interpolate использует это значение.", settings.outputFps, 0f, 240f, 1f, " fps") { onChange(settings.copy(outputFps = it)) }
                SegmentedSetting("Режим FPS", "keep = оставить; simple = ffmpeg -r; interpolate = ffmpeg minterpolate, медленно.", listOf("keep", "simple", "interpolate"), settings.fpsMode) { onChange(settings.copy(fpsMode = it)) }
                NumericSliderSetting("Качество видео", "Если доступен libx264 — используется как CRF. Если нет — автоматически переводится в q:v для mpeg4.", settings.crf, 0f, 51f, 1f, "") { onChange(settings.copy(crf = it)) }
                SegmentedSetting("Preset", "Скорость кодирования, используется когда доступен libx264.", listOf("ultrafast", "superfast", "veryfast", "faster", "fast", "medium", "slow"), settings.encoderPreset) { onChange(settings.copy(encoderPreset = it)) }
                ToggleSetting("Удалять исходные PNG после upscale", "Экономит место, как в старом скрипте.", settings.deleteFrames) { onChange(settings.copy(deleteFrames = it)) }
                ToggleSetting("Оставить рабочую папку", "Полезно для продолжения/отладки, но занимает много места.", settings.keepWork) { onChange(settings.copy(keepWork = it)) }
                NumericSliderSetting("Пауза каждые N кадров", "0 = без пауз. Для охлаждения телефона.", settings.pauseEvery, 0f, 1000f, 10f, "") { onChange(settings.copy(pauseEvery = it)) }
                NumericSliderSetting("Длительность паузы", "Секунд ожидания при паузе.", settings.pauseSeconds, 0f, 300f, 1f, " сек") { onChange(settings.copy(pauseSeconds = it)) }
            }
        }
    }
}

@Composable
fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFFDDD6FE))
}

@Composable
fun ToggleSetting(title: String, description: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onCheckedChange = onChecked)
        Column {
            Text(title)
            Text(description, style = MaterialTheme.typography.bodySmall, color = Color(0xFF9CA3AF))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SegmentedSetting(title: String, description: String, values: List<String>, selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold)
        Text(description, style = MaterialTheme.typography.bodySmall, color = Color(0xFF9CA3AF))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            values.forEach { value ->
                FilterChip(selected = selected == value, onClick = { onSelect(value) }, label = { Text(value) })
            }
        }
    }
}

@Composable
fun NumericSliderSetting(
    title: String,
    description: String,
    value: Float,
    min: Float,
    max: Float,
    step: Float,
    suffix: String,
    onValue: (Float) -> Unit,
) {
    var text by remember(value) { mutableStateOf(formatNumber(value)) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            OutlinedTextField(
                value = text,
                onValueChange = { raw ->
                    text = raw.replace(',', '.')
                    text.toFloatOrNull()?.let { onValue(it) }
                },
                singleLine = true,
                modifier = Modifier.width(116.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                suffix = { Text(suffix) },
            )
        }
        Text(description, style = MaterialTheme.typography.bodySmall, color = Color(0xFF9CA3AF))
        Slider(
            value = value.coerceIn(min, max),
            onValueChange = { raw ->
                val rounded = ((raw / step).roundToInt() * step).coerceIn(min, max)
                onValue(rounded)
                text = formatNumber(rounded)
            },
            valueRange = min..max,
        )
        if (value < min || value > max) {
            Text("Экспертное значение вне диапазона ползунка: ${formatNumber(value)}", style = MaterialTheme.typography.bodySmall, color = Color(0xFFFBBF24))
        }
    }
}

suspend fun processPhoto(context: Context, uri: Uri, settings: ClassicSettings, update: (String, Float) -> Unit): File = withContext(Dispatchers.IO) {
    withContext(Dispatchers.Main) { update("Подготовка фото...", 0.05f) }
    val input = copyUriToCache(context, uri, "anime4k_input", ".png")
    val output = File(context.cacheDir, "anime4k_photo_${System.currentTimeMillis()}.png")
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
    val finalFps = if (settings.outputFps > 0f) formatNumber(settings.outputFps) else originalFps

    withContext(Dispatchers.Main) { update("Разбор видео на PNG-кадры...", 0.08f) }
    runFfmpeg(
        context,
        listOf(
            "-hide_banner", "-y",
            "-i", input.absolutePath,
            "-vsync", "0",
            File(frames, "%08d.png").absolutePath,
        )
    )

    val frameFiles = frames.listFiles { file -> file.extension.lowercase() == "png" }?.sortedBy { it.name }.orEmpty()
    if (frameFiles.isEmpty()) error("Не удалось извлечь кадры")

    val jobs = settings.parallelJobs.roundToInt().coerceAtLeast(1)
    val semaphore = Semaphore(jobs)
    var done = 0
    val total = frameFiles.size
    val pauseEvery = settings.pauseEvery.roundToInt()
    val pauseMs = (settings.pauseSeconds * 1000).roundToInt().coerceAtLeast(0).toLong()

    withContext(Dispatchers.Main) { update("Anime4K обработка кадров: 0 / $total", 0.12f) }
    coroutineScope {
        frameFiles.mapIndexed { index, frame ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    val out = File(upscaled, frame.name)
                    if (!out.exists() || out.length() == 0L) {
                        val response = NativeBridge.processClassicImage(
                            frame.absolutePath,
                            out.absolutePath,
                            settings.scale.toDouble(),
                            settings.iterations.roundToInt().coerceAtLeast(1),
                            settings.pcs.toDouble(),
                            settings.pgs.toDouble(),
                        )
                        if (!response.startsWith("OK:")) error(response.removePrefix("ERR:"))
                    }
                    if (settings.deleteFrames) frame.delete()
                    val current = synchronized(work) { done += 1; done }
                    if (pauseEvery > 0 && current % pauseEvery == 0 && pauseMs > 0) Thread.sleep(pauseMs)
                    withContext(Dispatchers.Main) {
                        val p = 0.12f + (current.toFloat() / total.toFloat()) * 0.72f
                        update("Anime4K обработка кадров: $current / $total", p)
                    }
                }
            }
        }.awaitAll()
    }

    val output = File(context.cacheDir, "anime4k_video_${System.currentTimeMillis()}.mp4")
    withContext(Dispatchers.Main) { update("Сборка MP4...", 0.90f) }
    val hasX264 = hasFfmpegEncoder(context, "libx264")
    val command = mutableListOf(
        "-hide_banner", "-y",
        "-framerate", originalFps,
        "-i", File(upscaled, "%08d.png").absolutePath,
        "-i", input.absolutePath,
        "-map", "0:v:0",
        "-map", "1:a?",
    )
    if (settings.fpsMode == "simple" && settings.outputFps > 0f) {
        command += listOf("-r", finalFps)
    }
    if (settings.fpsMode == "interpolate" && settings.outputFps > 0f) {
        command += listOf("-vf", "minterpolate=fps=$finalFps:mi_mode=mci:mc_mode=aobmc:me_mode=bidir:vsbmc=1")
    }
    if (hasX264) {
        command += listOf(
            "-c:v", "libx264",
            "-crf", settings.crf.roundToInt().toString(),
            "-preset", settings.encoderPreset,
        )
    } else {
        val qv = crfToMpeg4Quality(settings.crf)
        command += listOf(
            "-c:v", "mpeg4",
            "-q:v", qv.toString(),
        )
    }
    command += listOf(
        "-pix_fmt", "yuv420p",
        "-c:a", "copy",
        "-shortest",
        output.absolutePath,
    )
    runFfmpeg(context, command)
    if (!settings.keepWork) work.deleteRecursively()
    withContext(Dispatchers.Main) { update("Видео готово", 1f) }
    output
}

fun ffmpegPath(context: Context): String {
    return File(context.applicationInfo.nativeLibraryDir, "libffmpeg.so").absolutePath
}

fun ffprobePath(context: Context): String {
    return File(context.applicationInfo.nativeLibraryDir, "libffprobe.so").absolutePath
}


fun hasFfmpegEncoder(context: Context, encoder: String): Boolean {
    return runCatching {
        runProcess(listOf(ffmpegPath(context), "-hide_banner", "-encoders")).contains(encoder)
    }.getOrDefault(false)
}

fun crfToMpeg4Quality(crf: Float): Int {
    // MPEG-4 encoder uses q:v, where 1 is best and 31 is worst.
    // Map common x264-like CRF values to a safe q:v fallback.
    return when {
        crf <= 12f -> 1
        crf <= 18f -> 2
        crf <= 23f -> 3
        crf <= 28f -> 5
        crf <= 35f -> 8
        else -> 12
    }
}

fun runProcess(args: List<String>, workingDir: File? = null): String {
    val process = ProcessBuilder(args)
        .redirectErrorStream(true)
        .apply { if (workingDir != null) directory(workingDir) }
        .start()
    val output = process.inputStream.bufferedReader().readText()
    val code = process.waitFor()
    if (code != 0) {
        error(output.ifBlank { "Command failed with code $code" })
    }
    return output
}

fun runFfmpeg(context: Context, args: List<String>) {
    runProcess(listOf(ffmpegPath(context)) + args)
}

fun probeFps(context: Context, file: File): String {
    val output = runProcess(
        listOf(
            ffprobePath(context),
            "-v", "error",
            "-select_streams", "v:0",
            "-show_entries", "stream=r_frame_rate",
            "-of", "default=nk=1:nw=1",
            file.absolutePath,
        )
    )
    return output.lineSequence().firstOrNull()?.trim().orEmpty()
}


fun formatNumber(value: Float): String {
    return if (value % 1f == 0f) value.roundToInt().toString() else String.format("%.2f", value)
}

fun copyUriToCache(context: Context, uri: Uri, prefix: String, suffix: String): File {
    val output = File(context.cacheDir, "${prefix}_${System.currentTimeMillis()}$suffix")
    context.contentResolver.openInputStream(uri).use { input ->
        FileOutputStream(output).use { out ->
            requireNotNull(input) { "Не удалось открыть файл" }.copyTo(out)
        }
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
    resolver.openOutputStream(uri).use { out ->
        file.inputStream().use { input -> requireNotNull(out).let { input.copyTo(it) } }
    }
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
