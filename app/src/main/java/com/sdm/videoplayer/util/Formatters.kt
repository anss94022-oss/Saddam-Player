package com.sdm.videoplayer.util

import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

fun formatDuration(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(ms)
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%02d:%02d", m, s)
}

fun formatSize(bytes: Long): String {
    if (bytes <= 0L) return "—"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1 -> String.format(Locale.US, "%.2f GB", gb)
        mb >= 1 -> String.format(Locale.US, "%.1f MB", mb)
        else -> String.format(Locale.US, "%.0f KB", kb)
    }
}

fun formatDate(epochSeconds: Long): String {
    if (epochSeconds <= 0L) return "—"
    return DateFormat.getDateInstance(DateFormat.MEDIUM)
        .format(Date(TimeUnit.SECONDS.toMillis(epochSeconds)))
}

fun formatResolution(width: Int, height: Int): String =
    if (width <= 0 || height <= 0) "—" else "$width × $height"

fun formatAspect(width: Int, height: Int): String {
    if (width <= 0 || height <= 0) return "—"
    fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
    val g = gcd(width, height).coerceAtLeast(1)
    return "${width / g}:${height / g}"
}

fun formatSpeed(speed: Float): String =
    if (speed == speed.toInt().toFloat()) String.format(Locale.US, "%.0f×", speed)
    else String.format(Locale.US, "%.2f×", speed)
