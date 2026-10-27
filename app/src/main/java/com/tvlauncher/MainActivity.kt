package com.tvlauncher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Apps in this list are only HIDDEN from this launcher. Nothing is uninstalled.
private val HIDDEN = setOf(
    "com.mitv.tvhome.mitvplus", "com.mitv.tvhome.michannel", "com.mitv.tvhome.atv",
    "com.mitv.videoplayer", "com.mitv.dream", "com.google.android.tvlauncher"
)

data class AppItem(val label: String, val pkg: String, val icon: ImageBitmap, val tint: Color)

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

    @Composable
    fun Home() {
        var apps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
        var focused by remember { mutableStateOf(0) }
        var clock by remember { mutableStateOf("") }
        LaunchedEffect(Unit) { apps = withContext(Dispatchers.IO) { loadApps() } }
        LaunchedEffect(Unit) {
            val f = SimpleDateFormat("h:mm a  •  EEE, d MMM", Locale.getDefault())
            while (true) { clock = f.format(Date()); delay(15_000) }
        }
        val current = apps.getOrNull(focused)
        val tint by animateColorAsState(current?.tint ?: Color(0xFF1B1B2F), label = "tint")

        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(tint, Color(0xFF0A0A0F), Color.Black))
            )
        ) {
            BasicText(
                clock,
                Modifier.align(Alignment.TopEnd).padding(36.dp),
                style = TextStyle(Color(0xCCFFFFFF), 22.sp)
            )
            if (current != null) {
                Column(Modifier.align(Alignment.CenterStart).padding(start = 64.dp, bottom = 120.dp)) {
                    Image(current.icon, null, Modifier.size(120.dp).clip(RoundedCornerShape(26.dp)))
                    Spacer(Modifier.height(20.dp))
                    BasicText(
                        current.label,
                        style = TextStyle(Color.White, 56.sp, FontWeight.Bold)
                    )
                }
            }
            LazyRow(
                Modifier.align(Alignment.BottomStart).padding(bottom = 48.dp),
                contentPadding = PaddingValues(horizontal = 64.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                itemsIndexed(apps) { idx, app ->
                    var isFocused by remember { mutableStateOf(false) }
                    val s by animateFloatAsState(if (isFocused) 1.18f else 1f, label = "scale")
                    Image(
                        app.icon, app.label,
                        Modifier
                            .size(132.dp)
                            .scale(s)
                            .onFocusChanged { isFocused = it.isFocused; if (it.isFocused) focused = idx }
                            .clip(RoundedCornerShape(24.dp))
                            .border(
                                if (isFocused) 3.dp else 0.dp,
                                Color.White, RoundedCornerShape(24.dp)
                            )
                            .clickable { launch(app.pkg) }
                    )
                }
            }
        }
    }
}
