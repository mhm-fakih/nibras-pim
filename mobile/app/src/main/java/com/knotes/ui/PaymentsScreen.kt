package com.knotes.ui

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.knotes.R
import com.knotes.expenses.PayCategory
import com.knotes.expenses.PayCurrencies
import com.knotes.expenses.PaymentCache
import com.knotes.expenses.PaymentOutbox
import com.knotes.expenses.PendingPayment
import com.knotes.expenses.parseAmount
import com.knotes.expenses.parseCategories
import com.knotes.expenses.parseCurrencies
import com.knotes.nibras.NibrasApi
import com.knotes.nibras.PushCenter
import com.knotes.nibras.ServerRefresh
import com.knotes.util.FriendlyTime
import com.knotes.util.autoDirection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID

/**
 * Record a payment on the phone, online or not.
 *
 * **Works with no server.** The categories and currencies are the copy last fetched (by this page
 * or by any sync), and a saved payment goes into [PaymentOutbox] first, always — the network is
 * only how it leaves. It is sent at once when the server is reachable, and otherwise on the next
 * reconnect or sync, by [PushCenter], whether or not this page is open. Each payment carries its
 * own id, so a send repeated after a lost reply is recorded once.
 *
 * Below the form: what is still waiting (with the server's reason when it refused one) and the
 * last payments that went up.
 */
@Composable
fun PaymentsScreen(baseUrl: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val api = remember { NibrasApi() }
    val cache = remember { PaymentCache(context) }
    remember { PaymentOutbox.seed(context) }

    var categories by remember { mutableStateOf<List<PayCategory>>(emptyList()) }
    var currencies by remember { mutableStateOf<PayCurrencies?>(null) }
    var savedAt by remember { mutableStateOf<Long?>(null) }
    var online by remember { mutableStateOf<Boolean?>(null) }
    var sending by remember { mutableStateOf(false) }

    val tick by ServerRefresh.tick.collectAsState()
    LaunchedEffect(baseUrl, tick) {
        online = null   // "checking", not the answer from the last visit
        withContext(Dispatchers.IO) {
            val c = cache.categories()
            val cur = cache.currencies()
            val at = cache.savedAt()
            withContext(Dispatchers.Main) {
                c?.let { categories = it }; cur?.let { currencies = it }; savedAt = at
            }
            if (baseUrl.isBlank()) {
                withContext(Dispatchers.Main) { online = false }
                return@withContext
            }
            val fresh = runCatching {
                api.fetchPaymentCategoriesRaw(baseUrl) to api.fetchCurrenciesRaw(baseUrl)
            }.getOrNull()
            if (fresh != null) {
                cache.saveCategories(fresh.first)
                cache.saveCurrencies(fresh.second)
                withContext(Dispatchers.Main) {
                    categories = parseCategories(fresh.first)
                    parseCurrencies(fresh.second)?.let { currencies = it }
                    savedAt = System.currentTimeMillis()
                    online = true
                }
                PushCenter.drainNow(context)
            } else {
                withContext(Dispatchers.Main) { online = false }
            }
        }
    }

    val pending by PaymentOutbox.pending.collectAsState()
    val sent by PaymentOutbox.sent.collectAsState()

    // The form. Currency and category stay after a save: payments come in runs of the same kind.
    var amountText by rememberSaveable { mutableStateOf("") }
    var currency by rememberSaveable { mutableStateOf("") }
    var categoryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var day by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    var paid by rememberSaveable { mutableStateOf(true) }
    var paidTouched by rememberSaveable { mutableStateOf(false) }
    var summary by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var removing by remember { mutableStateOf<PendingPayment?>(null) }

    val amount = parseAmount(amountText)
    val date = LocalDate.ofEpochDay(day)
    val shownCurrency = currency.ifBlank { currencies?.default.orEmpty() }

    fun save() {
        val a = amount ?: return
        val now = LocalDateTime.now()
        // Today keeps the time it was entered; another day is filed at noon, clear of any edge.
        val at = if (date == now.toLocalDate()) now.truncatedTo(ChronoUnit.SECONDS)
        else date.atTime(LocalTime.NOON)
        val item = PendingPayment(
            ref = UUID.randomUUID().toString(),
            amount = a,
            // The default as last fetched; blank only when no list was ever fetched — then the
            // server's default applies when it arrives.
            currency = shownCurrency,
            date = at.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            categoryId = categoryId,
            categoryName = categories.firstOrNull { it.id == categoryId }?.name.orEmpty(),
            summary = summary.trim(),
            notes = notes.trim(),
            paid = paid,
            queuedAt = System.currentTimeMillis(),
        )
        PaymentOutbox.queue(context, item)
        amountText = ""; summary = ""; notes = ""
        day = LocalDate.now().toEpochDay(); paid = true; paidTouched = false
        Toast.makeText(context, R.string.payments_saved, Toast.LENGTH_SHORT).show()
        PushCenter.drainAll(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.payments_title), style = MaterialTheme.typography.titleLarge)
        Text(
            when {
                online == true -> stringResource(R.string.payments_online)
                online == null && baseUrl.isNotBlank() -> stringResource(R.string.payments_checking)
                savedAt != null -> stringResource(R.string.payments_offline_saved, FriendlyTime.ago(context, savedAt!!))
                online == false -> stringResource(R.string.payments_offline_no_categories)
                else -> ""
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // ---- the form
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it.filter { c -> c.isDigit() || c in ".,' " } },
                label = { Text(stringResource(R.string.payments_amount)) },
                isError = amountText.isNotBlank() && amount == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Picker(
                label = stringResource(R.string.payments_currency),
                value = shownCurrency,
                options = currencies?.codes.orEmpty().map { it to it },
                onPick = { currency = if (it == currencies?.default) "" else it },
                modifier = Modifier.width(120.dp),
            )
        }
        Picker(
            label = stringResource(R.string.payments_category),
            value = categories.firstOrNull { it.id == categoryId }?.name
                ?: stringResource(R.string.payments_no_category),
            options = listOf<Pair<Long?, String>>(null to stringResource(R.string.payments_no_category)) +
                categories.map { it.id to it.name },
            onPick = { categoryId = it },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = {
                DatePickerDialog(context, { _, y, m, d ->
                    val picked = LocalDate.of(y, m + 1, d)
                    day = picked.toEpochDay()
                    // Not chosen by hand: a day ahead is a budgeted payment, the server's own rule.
                    if (!paidTouched) paid = !picked.isAfter(LocalDate.now())
                }, date.year, date.monthValue - 1, date.dayOfMonth).show()
            }) {
                Text(
                    if (date == LocalDate.now()) stringResource(R.string.payments_today)
                    else date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(if (paid) R.string.payments_paid else R.string.payments_to_be_paid),
                style = MaterialTheme.typography.bodyMedium,
            )
            Switch(checked = paid, onCheckedChange = { paid = it; paidTouched = true })
        }
        OutlinedTextField(
            value = summary,
            onValueChange = { summary = it },
            label = { Text(stringResource(R.string.payments_summary)) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.autoDirection(),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text(stringResource(R.string.payments_notes)) },
            minLines = 2,
            textStyle = MaterialTheme.typography.bodyLarge.autoDirection(),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = ::save, enabled = amount != null, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.payments_save))
        }

        // ---- waiting to be sent
        if (pending.isNotEmpty()) {
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    pluralStringResource(R.plurals.payments_waiting, pending.size, pending.size),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(enabled = !sending && baseUrl.isNotBlank(), onClick = {
                    sending = true
                    scope.launch {
                        PushCenter.drainNow(context)
                        sending = false
                        if (PaymentOutbox.sendable().isNotEmpty()) {
                            Toast.makeText(context, R.string.payments_not_reached, Toast.LENGTH_SHORT).show()
                        }
                    }
                }) { Text(stringResource(R.string.payments_send_now)) }
            }
            pending.forEach { p ->
                PaymentRow(p, categories) {
                    Row {
                        if (p.error != null) {
                            TextButton(onClick = {
                                PaymentOutbox.retry(context, p.ref); PushCenter.drainAll(context)
                            }) { Text(stringResource(R.string.payments_retry)) }
                        }
                        TextButton(onClick = { removing = p }) { Text(stringResource(R.string.payments_remove)) }
                    }
                }
            }
        }

        // ---- sent
        if (sent.isNotEmpty()) {
            HorizontalDivider()
            Text(stringResource(R.string.payments_sent), style = MaterialTheme.typography.titleSmall)
            sent.forEach { p -> PaymentRow(p, categories) {} }
        }
        Spacer(Modifier.height(24.dp))
    }

    removing?.let { p ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(stringResource(R.string.payments_remove_title)) },
            text = { Text(stringResource(R.string.payments_remove_text, money(p.amount, p.currency.ifBlank { shownCurrency }))) },
            confirmButton = {
                TextButton(onClick = { PaymentOutbox.remove(context, p.ref); removing = null }) {
                    Text(stringResource(R.string.payments_remove))
                }
            },
            dismissButton = {
                TextButton(onClick = { removing = null }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun PaymentRow(p: PendingPayment, categories: List<PayCategory>, actions: @Composable () -> Unit) {
    val category = p.categoryId?.let { id -> categories.firstOrNull { it.id == id }?.name } ?: p.categoryName
    val date = runCatching { LocalDateTime.parse(p.date).toLocalDate() }.getOrNull()
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (p.error != null) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    money(p.amount, p.currency),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    listOfNotNull(
                        category.ifBlank { null },
                        date?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
                        if (!p.paid) stringResource(R.string.payments_to_be_paid) else null,
                        if (p.serverId != null) "✓" else null,
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
            if (p.summary.isNotBlank()) {
                Text(p.summary, style = MaterialTheme.typography.bodyMedium.autoDirection())
            }
            if (p.notes.isNotBlank()) {
                Text(
                    p.notes,
                    style = MaterialTheme.typography.bodySmall.autoDirection(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            p.error?.let {
                Text(
                    stringResource(R.string.payments_refused, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            actions()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> Picker(
    label: String,
    value: String,
    options: List<Pair<T, String>>,
    onPick: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.autoDirection(),
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (key, text) ->
                DropdownMenuItem(
                    // Full width, so an Arabic name right-aligns instead of hugging the left edge.
                    text = {
                        Text(text, style = MaterialTheme.typography.bodyLarge.autoDirection(),
                            modifier = Modifier.fillMaxWidth())
                    },
                    onClick = { onPick(key); expanded = false },
                )
            }
        }
    }
}

/** 1,500.5 USD — grouped, up to two decimals; the currency code after (blank = server default). */
private fun money(amount: Double, currency: String): String {
    val f = NumberFormat.getNumberInstance(Locale.US).apply { maximumFractionDigits = 2 }
    return listOf(f.format(amount), currency).filter { it.isNotBlank() }.joinToString(" ")
}
