package com.knotes.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.knotes.prayer.Hijri
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val HOLIDAY = Color(0xFFD32F2F)

/**
 * A Shamsi month, Saturday first and right to left as Iran's calendar is printed: each day's Shamsi
 * number, with the Gregorian and Hijri days beneath; holidays (official ones and Fridays) in red, a
 * dot under a day with occasions. Tapping a day lists its three dates and its occasions, in Persian
 * with the Arabic. The Hijri day is the prayer page's, with its [hijriOffset].
 */
@Composable
fun ShamsiMonth(hijriOffset: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val official by IranCalendar.days.collectAsState()
    remember { IranCalendar.seed(context) }
    val today = LocalDate.now()
    val t = Shamsi.of(today)
    var ym by rememberSaveable { mutableStateOf(t.year * 12 + (t.month - 1)) }
    var selected by rememberSaveable { mutableStateOf(today.toEpochDay()) }
    val year = ym / 12
    val month = ym % 12 + 1
    val first = Shamsi.toGregorian(year, month, 1)
    val length = Shamsi.monthLength(year, month)
    val lead = Shamsi.weekdayIndex(first)
    val last = first.plusDays(length - 1L)
    val hFirst = Hijri.fromGregorian(first.year, first.monthValue, first.dayOfMonth, hijriOffset)
    val hLast = Hijri.fromGregorian(last.year, last.monthValue, last.dayOfMonth, hijriOffset)

    Column(modifier.fillMaxWidth()) {
        // Month and year, with the Gregorian and Hijri months it spans.
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { ym -= 1 }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "−") }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${Shamsi.MONTHS_FA[month - 1]} ${Shamsi.faDigits(year)}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    span(first, last) + "  ·  " +
                        (if (hFirst.month == hLast.month) hFirst.monthArabic else "${hFirst.monthArabic} – ${hLast.monthArabic}") +
                        " " + Shamsi.arDigits(hLast.year),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { ym += 1 }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "+") }
        }
        if (ym != t.year * 12 + (t.month - 1)) {
            TextButton(onClick = { ym = t.year * 12 + (t.month - 1); selected = today.toEpochDay() },
                modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("امروز") }
        }

        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Column {
                Row(Modifier.fillMaxWidth()) {
                    listOf("ش", "ی", "د", "س", "چ", "پ", "ج").forEachIndexed { i, w ->
                        Text(w, Modifier.weight(1f), textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (i == 6) HOLIDAY else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                val cells = lead + length
                val rows = (cells + 6) / 7
                for (r in 0 until rows) {
                    Row(Modifier.fillMaxWidth()) {
                        for (c in 0 until 7) {
                            val i = r * 7 + c - lead
                            Box(Modifier.weight(1f).aspectRatio(0.82f).padding(1.dp)) {
                                if (i in 0 until length) {
                                    val date = first.plusDays(i.toLong())
                                    DayCell(date, i + 1, hijriOffset, official[date], date == today,
                                        date.toEpochDay() == selected) { selected = date.toEpochDay() }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        DayDetails(LocalDate.ofEpochDay(selected), hijriOffset, official[LocalDate.ofEpochDay(selected)])
    }
}

private fun span(a: LocalDate, b: LocalDate): String {
    val f = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)
    return if (a.month == b.month) "${a.format(f)} ${b.year}"
    else if (a.year == b.year) "${a.format(f)}–${b.format(f)} ${b.year}"
    else "${a.format(f)} ${a.year}–${b.format(f)} ${b.year}"
}

@Composable
private fun DayCell(
    date: LocalDate,
    shamsiDay: Int,
    hijriOffset: Int,
    official: OfficialDay?,
    isToday: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val h = Hijri.fromGregorian(date.year, date.monthValue, date.dayOfMonth, hijriOffset)
    val holiday = official?.holiday == true || date.dayOfWeek == DayOfWeek.FRIDAY
    val shape = RoundedCornerShape(8.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, shape)
            .then(if (isToday) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(Shamsi.faDigits(shamsiDay), fontSize = 17.sp, fontWeight = FontWeight.Bold,
            color = if (holiday) HOLIDAY else MaterialTheme.colorScheme.onSurface)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(date.dayOfMonth.toString(), fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(Shamsi.arDigits(h.day), fontSize = 9.sp, color = MaterialTheme.colorScheme.tertiary)
        }
        Box(
            Modifier.size(4.dp).background(
                if (official?.occasions?.isNotEmpty() == true) (if (holiday) HOLIDAY else MaterialTheme.colorScheme.primary) else Color.Transparent,
                CircleShape,
            ),
        )
    }
}

/** A day's three dates and its occasions, Persian then Arabic, right-aligned. */
@Composable
fun DayDetails(date: LocalDate, hijriOffset: Int, official: OfficialDay?) {
    val s = Shamsi.of(date)
    val h = Hijri.fromGregorian(date.year, date.monthValue, date.dayOfMonth, hijriOffset)
    val holiday = official?.holiday == true || date.dayOfWeek == DayOfWeek.FRIDAY
    val rtl = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Rtl)
    Column(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .padding(10.dp),
    ) {
        Text(
            "${Shamsi.WEEKDAYS_FA[Shamsi.weekdayIndex(date)]} ${Shamsi.faDigits(s.day)} ${s.monthFa} ${Shamsi.faDigits(s.year)}",
            style = rtl, fontWeight = FontWeight.Bold, textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth(),
            color = if (holiday) HOLIDAY else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            "${Shamsi.arDigits(h.day)} ${h.monthArabic} ${Shamsi.arDigits(h.year)}  ·  ⁦${date.format(DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH))}⁩",
            style = rtl, textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        official?.hijriOfficial?.let { (y, m, d) ->
            if (d != h.day || m != h.month) {
                Text("التقويم الرسمي: ${Shamsi.arDigits(d)} ${Hijri.MONTHS_AR[m - 1]} ${Shamsi.arDigits(y)}",
                    style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.Rtl),
                    textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val occ = official?.occasions.orEmpty()
        if (occ.isEmpty()) {
            Text(if (official == null) "—" else "بدون مناسبت", style = rtl, textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        occ.forEach { o ->
            Text(o.fa, style = rtl, textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                color = if (o.holiday) HOLIDAY else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
            if (o.ar.isNotBlank()) {
                Text(o.ar, style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Rtl),
                    textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
