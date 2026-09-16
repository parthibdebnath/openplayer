package com.musicplayer.app.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class FormattedTime(val time: String, val amPm: String?)

/**
 * Parsing a pattern is the expensive part of SimpleDateFormat, and the clock reformats once a
 * second on every screen, so the formatters are built once and reused. Only ever touched from the
 * main thread, which is what makes reusing these non-thread-safe objects safe.
 */
private val twentyFourHourFormat by lazy { SimpleDateFormat("HH:mm", Locale.getDefault()) }
private val twelveHourFormat by lazy { SimpleDateFormat("h:mm", Locale.getDefault()) }
private val amPmFormat by lazy { SimpleDateFormat("a", Locale.getDefault()) }
private val dateFormat by lazy { SimpleDateFormat("EEE MMM d", Locale.getDefault()) }

/** e.g. "9:41" + "AM" for 12-hour, or "21:41" + null for 24-hour. */
fun formatClockTime(date: Date, twentyFourHour: Boolean): FormattedTime =
    if (twentyFourHour) {
        FormattedTime(twentyFourHourFormat.format(date), null)
    } else {
        FormattedTime(
            twelveHourFormat.format(date),
            amPmFormat.format(date).uppercase(Locale.getDefault()),
        )
    }

/** "Day Mon DD" e.g. "Mon Sep 21". */
fun formatDate(date: Date): String = dateFormat.format(date)

fun formatDurationMs(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
}
