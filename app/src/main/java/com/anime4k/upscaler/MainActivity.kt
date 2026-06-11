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
    val mode: String = "a",
    val scale: Float = 2f,
    val quality: String = "balanced",
    val iterations: Float = 1f,
    val pcs: Float = 0f,
    val pgs: Float = 1f,
    val denoise: String = "off",
    val deblur: String = "off",
    val lineDarken: Float = 0.15f,
    val lineThin: Float = 0f,
    val clampHighlights: Boolean = false,
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
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Настройки качества", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            SegmentedSetting("Режим", "Выбор цепочки обработки под тип исходника.", listOf("legacy", "a", "b", "c", "aa", "bb", "ca"), settings.mode) { onChange(settings.copy(mode = it)) }
            SliderSetting("Масштаб", "Во сколько раз увеличить картинку. Для 1080p → 4K обычно x2.", settings.scale, 1f, 4f, 0.5f) { onChange(settings.copy(scale = it)) }
            SegmentedSetting("Качество", "Баланс скорости и силы обработки.", listOf("fast", "balanced", "high", "ultra"), settings.quality) { onChange(settings.copy(quality = it)) }
            SliderSetting("Итерации", "Повторение восстановления линий. Больше — сильнее эффект, но медленнее.", settings.iterations, 1f, 4f, 1f) { onChange(settings.copy(iterations = it)) }
            SliderSetting("Push Gradient", "Главная сила восстановления контуров и линий.", settings.pgs, 0f, 2f, 0.05f) { onChange(settings.copy(pgs = it)) }
            SliderSetting("Push Color", "Подтягивает цветовые области. Обычно 0–0.4, слишком много может мылить.", settings.pcs, 0f, 1f, 0.05f) { onChange(settings.copy(pcs = it)) }
            SegmentedSetting("Шумоподавление", "Для старого, сжатого или 480p/720p видео.", listOf("off", "low", "medium", "high"), settings.denoise) { onChange(settings.copy(denoise = it)) }
            SegmentedSetting("Deblur", "Повышает резкость после шума/размытия. Сильный режим может давать ореолы.", listOf("off", "low", "medium", "high"), settings.deblur) { onChange(settings.copy(deblur = it)) }
            SliderSetting("Затемнение линий", "Делает контуры глубже и визуально ближе к 4K-ремастеру.", settings.lineDarken, 0f, 1f, 0.05f) { onChange(settings.copy(lineDarken = it)) }
            SliderSetting("Утончение линий", "Осветляет края толстых линий. Использовать аккуратно.", settings.lineThin, 0f, 1f, 0.05f) { onChange(settings.copy(lineThin = it)) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(settings.clampHighlights, onCheckedChange = { onChange(settings.copy(clampHighlights = it)) })
                Column {
                    Text("Защита светлых областей")
                    Text("Снижает пересветы, ringing и выбросы после резкости.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF9CA3AF))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SegmentedSetting(title: String, description: String, values: List<String>, selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold)
        Text(description, style = MaterialTheme.typography.bodySmall, color = Color(0xFF9CA3AF))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            values.forEach { value ->
                FilterChip(selected = selected == value, onClick = { onSelect(value) }, label = { Text(value) })
            }
        }
    }
}

@Composable
fun SliderSetting(title: String, description: String, value: Float, min: Float, max: Float, step: Float, onValue: (Float) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(String.format("%.2f", value), color = Color(0xFF22D3EE))
        }
        Text(description, style = MaterialTheme.typography.bodySmall, color = Color(0xFF9CA3AF))
        Slider(
            value = value,
            onValueChange = { raw -> onValue(((raw / step).roundToInt() * step).coerceIn(min, max)) },
            valueRange = min..max,
        )
    }
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
