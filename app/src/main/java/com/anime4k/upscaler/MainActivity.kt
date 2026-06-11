package com.anime4k.upscaler

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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

data class UpscaleSettings(
    val mode: String = "legacy",
    val scale: Float = 2f,
    val quality: String = "balanced",
    val iterations: Float = 3f,
    val pcs: Float = 0f,
    val pgs: Float = 1f,
    val denoise: String = "off",
    val deblur: String = "off",
    val lineDarken: Float = 0f,
    val lineThin: Float = 0f,
    val clampHighlights: Boolean = false,
    val parallelJobs: Float = 2f,
    val outputFps: Float = 0f,
    val fpsMode: String = "keep",
    val crf: Float = 18f,
    val encoderPreset: String = "veryfast",
    val deleteFrames: Boolean = true,
    val pauseEvery: Float = 0f,
    val pauseSeconds: Float = 10f,
)

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
        var settings by remember { mutableStateOf(UpscaleSettings()) }
        var inputUri by remember { mutableStateOf<Uri?>(null) }
        var outputFile by remember { mutableStateOf<File?>(null) }
        var status by remember { mutableStateOf("Выберите изображение и настройте качество") }
        var processing by remember { mutableStateOf(false) }

        val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            inputUri = uri
            outputFile = null
            status = if (uri != null) "Изображение выбрано" else "Выбор отменён"
        }

        Scaffold(
            topBar = { TopAppBar(title = { Text("Anime4K Апскейлер") }) }
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    "Rust-ядро Anime4K для апскейла аниме. Сейчас рабочий MVP обрабатывает изображения; видео добавим следующим этапом через очередь кадров.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFD1D5DB),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { picker.launch("image/*") }) { Text("Выбрать картинку") }
                    Button(
                        enabled = inputUri != null && !processing,
                        onClick = {
                            val uri = inputUri ?: return@Button
                            scope.launch {
                                processing = true
                                status = "Подготовка файла..."
                                val result = withContext(Dispatchers.IO) {
                                    runCatching {
                                        val input = copyUriToCache(context as android.content.Context, uri)
                                        val output = File(context.cacheDir, "anime4k_${System.currentTimeMillis()}.png")
                                        val response = NativeBridge.processImage(
                                            input.absolutePath,
                                            output.absolutePath,
                                            settings.mode,
                                            settings.scale.toDouble(),
                                            settings.quality,
                                            settings.iterations.roundToInt(),
                                            settings.pcs.toDouble(),
                                            settings.pgs.toDouble(),
                                            settings.denoise,
                                            settings.deblur,
                                            settings.lineDarken.toDouble(),
                                            settings.lineThin.toDouble(),
                                            settings.clampHighlights,
                                        )
                                        response to output
                                    }
                                }
                                result.onSuccess { (response, file) ->
                                    if (response.startsWith("OK:")) {
                                        outputFile = file
                                        status = "Готово: ${file.name}"
                                    } else {
                                        status = response.removePrefix("ERR:")
                                    }
                                }.onFailure { status = "Ошибка: ${it.message}" }
                                processing = false
                            }
                        }
                    ) { Text(if (processing) "Обработка..." else "Запустить") }
                }

                Text(status, color = Color(0xFFE5E7EB))

                ImagePreview("Вход", inputUri)
                outputFile?.let { ImagePreview("Результат", Uri.fromFile(it)) }

                SettingsCard(settings) { settings = it }
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
fun SettingsCard(settings: UpscaleSettings, onChange: (UpscaleSettings) -> Unit) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF111827))) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("Настройки качества", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "Ползунки теперь шире, а справа есть точный ввод числа. Если нужно значение больше диапазона ползунка — введи вручную.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF9CA3AF),
            )

            SectionTitle("Основной алгоритм")
            SegmentedSetting("Режим", "legacy = старый рабочий Anime4K-rs. Остальные режимы — экспериментальные пресеты ядра.", listOf("legacy", "a", "b", "c", "aa", "bb", "ca", "custom"), settings.mode) { onChange(settings.copy(mode = it)) }
            NumericSliderSetting("Масштаб", "Во сколько раз увеличить. Нормально: 2 для 1080p→4K, 4 для слабого исходника. Можно ввести больше вручную.", settings.scale, 0.5f, 8f, 0.25f, "x") { onChange(settings.copy(scale = it)) }
            SegmentedSetting("Качество", "Для legacy почти не влияет; для экспериментальных режимов задаёт силу обработки.", listOf("fast", "balanced", "high", "ultra"), settings.quality) { onChange(settings.copy(quality = it)) }
            NumericSliderSetting("Итерации", "Количество прогонов старого Anime4K. В старом скрипте хорошее значение было 3. Большие числа сильно замедляют.", settings.iterations, 1f, 20f, 1f, "") { onChange(settings.copy(iterations = it)) }
            NumericSliderSetting("Push Gradient", "Главная сила контуров. Обычно 1.0. Можно пробовать 1.2–2.0, выше — экспериментально.", settings.pgs, 0f, 10f, 0.05f, "") { onChange(settings.copy(pgs = it)) }
            NumericSliderSetting("Push Color", "Подтягивание цвета. Обычно 0.0. Можно пробовать 0.1–1.0, выше — экспериментально.", settings.pcs, 0f, 10f, 0.05f, "") { onChange(settings.copy(pcs = it)) }

            SectionTitle("Дополнительная обработка")
            SegmentedSetting("Шумоподавление", "Для старого, сжатого или 480p/720p видео. В legacy можно оставить off.", listOf("off", "low", "medium", "high"), settings.denoise) { onChange(settings.copy(denoise = it)) }
            SegmentedSetting("Deblur", "Повышает резкость. Сильный режим может давать ореолы.", listOf("off", "low", "medium", "high"), settings.deblur) { onChange(settings.copy(deblur = it)) }
            NumericSliderSetting("Затемнение линий", "Глубина контуров. Для classic/legacy обычно 0, чтобы не менять старое качество.", settings.lineDarken, 0f, 3f, 0.05f, "") { onChange(settings.copy(lineDarken = it)) }
            NumericSliderSetting("Утончение линий", "Осветляет края толстых линий. Лучше использовать осторожно.", settings.lineThin, 0f, 3f, 0.05f, "") { onChange(settings.copy(lineThin = it)) }
            ToggleSetting("Защита светлых областей", "Снижает пересветы, ringing и выбросы после резкости.", settings.clampHighlights) { onChange(settings.copy(clampHighlights = it)) }

            SectionTitle("Видео и производительность")
            Text(
                "Эти параметры нужны для следующего видео-экрана и повторяют старый anime4k_video.sh. Оперативный RAM-буфер не добавляю — оставляем файловый pipeline.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF9CA3AF),
            )
            NumericSliderSetting("Одновременных upscale-процессов", "Параллельная обработка кадров. 1 = старое поведение, 2–4 = быстрый режим для мощного телефона. Можно ввести больше вручную.", settings.parallelJobs, 1f, 16f, 1f, "") { onChange(settings.copy(parallelJobs = it)) }
            NumericSliderSetting("Выходной FPS", "0 = оставить оригинальный FPS. Можно ввести 24, 30, 60, 120 и любые другие числа.", settings.outputFps, 0f, 240f, 1f, " fps") { onChange(settings.copy(outputFps = it)) }
            SegmentedSetting("Режим FPS", "keep = не менять; simple = ffmpeg -r; interpolate = minterpolate, медленно, но плавнее.", listOf("keep", "simple", "interpolate"), settings.fpsMode) { onChange(settings.copy(fpsMode = it)) }
            NumericSliderSetting("CRF", "Качество кодирования H.264/H.265. Меньше = лучше и больше файл. Обычно 16–20.", settings.crf, 0f, 51f, 1f, "") { onChange(settings.copy(crf = it)) }
            SegmentedSetting("Preset кодека", "Скорость кодирования ffmpeg. Медленнее = обычно меньше файл при том же CRF.", listOf("ultrafast", "superfast", "veryfast", "faster", "fast", "medium", "slow"), settings.encoderPreset) { onChange(settings.copy(encoderPreset = it)) }
            ToggleSetting("Удалять исходные PNG после upscale", "Экономит место, как в старом скрипте.", settings.deleteFrames) { onChange(settings.copy(deleteFrames = it)) }
            NumericSliderSetting("Пауза каждые N кадров", "0 = без пауз. Полезно для охлаждения телефона.", settings.pauseEvery, 0f, 1000f, 10f, "") { onChange(settings.copy(pauseEvery = it)) }
            NumericSliderSetting("Длительность паузы", "Сколько секунд ждать для охлаждения.", settings.pauseSeconds, 0f, 300f, 1f, " сек") { onChange(settings.copy(pauseSeconds = it)) }
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
            Text("Введено Экспертное значение вне диапазона ползунка: ${formatNumber(value)}", style = MaterialTheme.typography.bodySmall, color = Color(0xFFFBBF24))
        }
    }
}

fun formatNumber(value: Float): String {
    return if (value % 1f == 0f) value.roundToInt().toString() else String.format("%.2f", value)
}

fun copyUriToCache(context: android.content.Context, uri: Uri): File {
    val output = File(context.cacheDir, "anime4k_input_${System.currentTimeMillis()}.png")
    context.contentResolver.openInputStream(uri).use { input ->
        FileOutputStream(output).use { out ->
            requireNotNull(input) { "Не удалось открыть файл" }.copyTo(out)
        }
    }
    return output
}
