package com.tvlauncher

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

// Change this to your city for the weather card.
private const val CITY = "Bhopal"

// Apps in this list are only HIDDEN from this launcher. Nothing is uninstalled.
private val HIDDEN = setOf(
    "com.mitv.tvhome.mitvplus", "com.mitv.tvhome.michannel", "com.mitv.tvhome.atv",
    "com.mitv.videoplayer", "com.mitv.dream", "com.google.android.tvlauncher"
)

data class AppItem(val label: String, val pkg: String, val icon: ImageBitmap, val tint: Color)
data class Weather(val temp: Int, val text: String, val city: String)

private fun httpGet(u: String): String {
    val c = URL(u).openConnection() as HttpURLConnection
    c.connectTimeout = 8000
    c.readTimeout = 8000
    return try { c.inputStream.bufferedReader().readText() } finally { c.disconnect() }
}

private fun codeText(c: Int): String = when (c) {
    0 -> "Clear"
    1, 2, 3 -> "Partly cloudy"
    45, 48 -> "Fog"
    in 51..67 -> "Rain"
    in 71..77 -> "Snow"
    in 80..82 -> "Showers"
    in 95..99 -> "Thunderstorm"
    else -> "Weather"
}

private fun fetchWeather(): Weather? = try {
    val q = URLEncoder.encode(CITY, "UTF-8")
    val g = JSONObject(httpGet("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=1"))
        .getJSONArray("results").getJSONObject(0)
    val lat = g.getDouble("latitude")
    val lon = g.getDouble("longitude")
    val cur = JSONObject(
        httpGet("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code")
    ).getJSONObject("current")
    Weather(cur.getDouble("temperature_2m").roundToInt(), codeText(cur.getInt("weather_code")), g.getString("name"))
} catch (e: Exception) { null }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Home() }
    }

    private fun loadApps(): List<AppItem> {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        var list = pm.queryIntentActivities(intent, 0)
        if (list.isEmpty()) {
            list = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        }
        return list.map { it.activityInfo.packageName to it }
            .filter { it.first != packageName && it.first !in HIDDEN }
            .distinctBy { it.first }
            .map { (pkg, ri) ->
                val hue = ((pkg.hashCode() and 0x7fffffff) % 360).toFloat()
                AppItem(
                    ri.loadLabel(pm).toString(), pkg,
                    ri.loadIcon(pm).toBitmap(256, 256).asImageBitmap(),
                    Color.hsv(hue, 0.55f, 0.45f)
                )
            }.sortedBy { it.label.lowercase() }
    }

    private fun launch(pkg: String) {
        val i = packageManager.getLeanbackLaunchIntentForPackage(pkg)
            ?: packageManager.getLaunchIntentForPackage(pkg)
        if (i != null) startActivity(i)
    }

    private fun openAssistant() {
        val tries = listOf(Intent(Intent.ACTION_ASSIST), Intent(Intent.ACTION_VOICE_COMMAND))
        for (i in tries) {
            try {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(i)
                return
            } catch (e: Exception) { }
        }
        Toast.makeText(this, "Voice assistant not found", Toast.LENGTH_SHORT).show()
    }

    @Composable
    fun GlassButton(text: String, onClick: () -> Unit) {
        var f by remember { mutableStateOf(false) }
        val shape = RoundedCornerShape(16.dp)
        Box(
            Modifier
                .onFocusChanged { f = it.isFocused }
                .clip(shape)
                .background(if (f) Color(0x40FFFFFF) else Color(0x1AFFFFFF))
                .border(if (f) 3.dp else 1.dp, if (f) Color.White else Color(0x40FFFFFF), shape)
                .clickable { onClick() }
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) { BasicText(text, style = TextStyle(Color.White, 16.sp)) }
    }

    @Composable
    fun Glass(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
        val shape = RoundedCornerShape(28.dp)
        Column(
            modifier
                .clip(shape)
                .background(Color(0x26FFFFFF))
                .border(1.dp, Color(0x40FFFFFF), shape)
                .padding(22.dp),
            content = content
        )
    }

    @Composable
    fun Home() {
        var apps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
        var focused by remember { mutableStateOf(0) }
        var now by remember { mutableStateOf(Date()) }
        var weather by remember { mutableStateOf<Weather?>(null) }
        val prefs = remember { getSharedPreferences("prefs", MODE_PRIVATE) }
        var wpIndex by remember {
            mutableStateOf(prefs.getInt("wp", 0).coerceIn(0, WALLPAPERS.lastIndex))
        }
        val wp = WALLPAPERS[wpIndex]
        val c0 by animateColorAsState(Color(wp.top), tween(800), label = "c0")
        val c1 by animateColorAsState(Color(wp.mid), tween(800), label = "c1")
        val c2 by animateColorAsState(Color(wp.bottom), tween(800), label = "c2")

        LaunchedEffect(Unit) { apps = withContext(Dispatchers.IO) { loadApps() } }
        LaunchedEffect(Unit) { while (true) { now = Date(); delay(15_000) } }
        LaunchedEffect(Unit) {
            while (true) {
                val w = withContext(Dispatchers.IO) { fetchWeather() }
                if (w != null) weather = w
                delay(if (w == null) 60_000L else 1_800_000L)
            }
        }

        val current = apps.getOrNull(focused)
        val tint by animateColorAsState(current?.tint ?: Color(0xFF1B3A52), label = "tint")
        val hour = Calendar.getInstance().apply { time = now }.get(Calendar.HOUR_OF_DAY)
        val greeting = when {
            hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"
            else -> "Good evening"
        }
        val timeText = SimpleDateFormat("h:mm", Locale.getDefault()).format(now)
        val ampm = SimpleDateFormat("a", Locale.getDefault()).format(now)
        val dateText = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now)

        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(c0, c1, c2))
            )
        ) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.radialGradient(listOf(tint.copy(alpha = 0.40f), Color.Transparent))
                )
            )
            Column(Modifier.fillMaxSize().padding(top = 48.dp, bottom = 32.dp)) {
                Row(
                    Modifier.padding(horizontal = 64.dp).fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Glass(Modifier.weight(1f).fillMaxHeight()) {
                        BasicText(greeting, style = TextStyle(Color(0xB3FFFFFF), 20.sp))
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            BasicText(timeText, style = TextStyle(Color.White, 60.sp, FontWeight.Bold))
                            Spacer(Modifier.width(10.dp))
                            BasicText(
                                ampm,
                                Modifier.padding(bottom = 10.dp),
                                style = TextStyle(Color(0xB3FFFFFF), 22.sp)
                            )
                        }
                        BasicText(dateText, style = TextStyle(Color(0xCCFFFFFF), 22.sp))
                        Spacer(Modifier.height(8.dp))
                        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x40FFFFFF)))
                        Spacer(Modifier.height(8.dp))
                        val w = weather
                        if (w != null) {
                            BasicText("${w.temp}°", style = TextStyle(Color.White, 32.sp, FontWeight.Bold))
                            BasicText("${w.text} · ${w.city}", style = TextStyle(Color(0xB3FFFFFF), 18.sp))
                        } else {
                            BasicText("Weather loading…", style = TextStyle(Color(0x99FFFFFF), 20.sp))
                        }
                    }
                    Glass(Modifier.weight(1f).fillMaxHeight()) {
                        if (current != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(
                                    current.icon, null,
                                    Modifier.size(96.dp).clip(RoundedCornerShape(22.dp))
                                )
                                Spacer(Modifier.width(24.dp))
                                Column {
                                    BasicText(
                                        current.label,
                                        style = TextStyle(Color.White, 40.sp, FontWeight.Bold)
                                    )
                                    BasicText(
                                        "Press OK to open",
                                        style = TextStyle(Color(0xB3FFFFFF), 20.sp)
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            GlassButton("Wallpaper") {
                                wpIndex = (wpIndex + 1) % WALLPAPERS.size
                                prefs.edit().putInt("wp", wpIndex).apply()
                            }
                            GlassButton("Assistant") { openAssistant() }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 56.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    itemsIndexed(apps) { idx, app ->
                        var isFocused by remember { mutableStateOf(false) }
                        val s by animateFloatAsState(if (isFocused) 1.15f else 1f, label = "scale")
                        val shape = RoundedCornerShape(24.dp)
                        Column(
                            Modifier
                                .width(136.dp)
                                .scale(s)
                                .onFocusChanged { isFocused = it.isFocused; if (it.isFocused) focused = idx }
                                .clip(shape)
                                .background(if (isFocused) Color(0x33FFFFFF) else Color.Transparent)
                                .border(if (isFocused) 3.dp else 0.dp, Color.White, shape)
                                .clickable { launch(app.pkg) }
                                .padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Image(app.icon, app.label, Modifier.size(112.dp).clip(RoundedCornerShape(20.dp)))
                            Spacer(Modifier.height(6.dp))
                            BasicText(
                                app.label, maxLines = 1,
                                style = TextStyle(if (isFocused) Color.White else Color(0x99FFFFFF), 15.sp)
                            )
                        }
                    }
                }
            }
        }
    }
}
