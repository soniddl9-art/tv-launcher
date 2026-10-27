package com.tvlauncher

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.service.dreams.DreamService
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

// Screen saver: slow-moving clock on the chosen wallpaper (moves to avoid screen burn-in).
class SaverService : DreamService() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var root: FrameLayout
    private lateinit var box: LinearLayout
    private lateinit var timeView: TextView
    private lateinit var dateView: TextView

    private val tick = object : Runnable {
        override fun run() {
            update()
            handler.postDelayed(this, 20_000)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        val idx = getSharedPreferences("prefs", MODE_PRIVATE).getInt("wp", 0)
            .coerceIn(0, WALLPAPERS.lastIndex)
        val wp = WALLPAPERS[idx]
        root = FrameLayout(this)
        root.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(wp.top.toInt(), wp.mid.toInt(), wp.bottom.toInt())
        )
        timeView = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 80f
            typeface = Typeface.DEFAULT_BOLD
        }
        dateView = TextView(this).apply {
            setTextColor(0xB3FFFFFF.toInt())
            textSize = 24f
        }
        box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(timeView)
            addView(dateView)
        }
        root.addView(
            box,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )
        setContentView(root)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        handler.post(tick)
    }

    override fun onDreamingStopped() {
        handler.removeCallbacksAndMessages(null)
        super.onDreamingStopped()
    }

    private fun update() {
        val now = Date()
        timeView.text = SimpleDateFormat("h:mm a", Locale.getDefault()).format(now)
        dateView.text = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now)
        root.post {
            val maxX = (root.width - box.width).coerceAtLeast(0)
            val maxY = (root.height - box.height).coerceAtLeast(0)
            box.animate()
                .x(Random.nextInt(maxX + 1).toFloat())
                .y(Random.nextInt(maxY + 1).toFloat())
                .setDuration(3000)
                .start()
        }
    }
}
