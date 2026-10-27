package com.tvlauncher

// Colours are top, middle, bottom of the background gradient (ARGB).
data class Wallpaper(val name: String, val top: Long, val mid: Long, val bottom: Long)

val WALLPAPERS = listOf(
    Wallpaper("Rain Glass", 0xFF16293A, 0xFF0B141D, 0xFF05080C),
    Wallpaper("Sunset", 0xFF4A2A5C, 0xFF7A2E3A, 0xFF140A10),
    Wallpaper("Aurora", 0xFF0B4A44, 0xFF123A5A, 0xFF050A14),
    Wallpaper("Midnight", 0xFF14142A, 0xFF07070E, 0xFF000000),
    Wallpaper("Forest", 0xFF1A4A2E, 0xFF0B1A12, 0xFF050A07),
    Wallpaper("Amber", 0xFF5A3A14, 0xFF1F1209, 0xFF0A0604)
)
