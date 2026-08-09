package com.knotes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.knotes.Tag
import com.knotes.nibras.NibrasApi
import com.knotes.nibras.NibrasRecord
import com.knotes.nibras.htmlToPlain
import kotlinx.coroutines.launch

/**
 * Search the BACKEND, not the phone.
 *
 * Every other browsing screen reads what sync already pulled down, which is only ever the
 * *bookmarked* records — so anything not yet flagged is invisible on the phone no matter how you
 * look for it. This page queries the server directly and gives each result a bookmark button:
 * flagging is what puts a record into the export, so the next sync brings it down for offline use.
 * That is the whole loop, and it is why the button says "sync on next pull" rather than "saved".
 *
 * Results arrive in the export's own shape ([NibrasRecord]), so they render with the same fields
 * the browser screen shows rather than a parallel model that could drift from it.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BackendSearchScreen(
    baseUrl: String,
    username: String,
    /** Enabled modules, from the server — the same list the capture screen offers. */
    types: List<Tag>,
) {
    val api = remember { NibrasApi() }
    val scope = rememberCoroutineScope()

    var query by rememberSaveable { mutableStateOf("") }
    var ecode by rememberSaveable { mutableStateOf("") }   // "" = every enabled module
    var results by remember { mutableStateOf<List<NibrasRecord>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    // Per-record flag state, keyed by "<ecode>:<id>". Held here rather than mutating the record so
    // a failed push can fall back without inventing a fake record.
    val flagged = remember { mutableStateMapOf<String, Boolean>() }

    fun runSearch() {
        if (query.isBlank() || baseUrl.isBlank()) {
            message = if (baseUrl.isBlank()) "Set the Nibras server URL in Settings first" else null
            return
        }
        searching = true
        message = null
        scope.launch {
            val outcome = runCatching { api.searchBackend(baseUrl, query.trim(), ecode) }
            searching = false
            outcome
                .onSuccess { hits ->
                    results = hits
                    flagged.clear()
                    hits.forEach { flagged["${it.ecode}:${it.id}"] = it.bookmarked }
                    message = if (hits.isEmpty()) "No matches" else null
                }
                .onFailure { e ->
                    results = emptyList()
                    message = describeFailure(e, baseUrl)
                }
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Search server") }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                label = { Text("Search the Nibras server") },
                trailingIcon = {
                    IconButton(onClick = { runSearch() }) { Icon(Icons.Filled.Search, "Search") }
                },
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { runSearch() }),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            )

            // Type filter. "All" first, then one chip per enabled module — the server decides which
            // those are, so a module switched off in Settings never appears here.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            ) {
                FilterChip(
                    selected = ecode.isEmpty(),
                    onClick = { ecode = ""; if (query.isNotBlank()) runSearch() },
                    label = { Text("All", style = MaterialTheme.typography.labelSmall) },
                )
                types.forEach { t ->
                    FilterChip(
                        selected = ecode == t.code,
                        onClick = { ecode = t.code; if (query.isNotBlank()) runSearch() },
                        // Code only, matching the note filters: the full names wrap the chip row
                        // onto several lines at phone width and add nothing the letter doesn't say.
                        label = { Text(t.code, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            when {
                searching -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                message != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        message!!,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 10.dp, end = 10.dp, bottom = 12.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(results, key = { "${it.ecode}:${it.id}" }) { rec ->
                        val key = "${rec.ecode}:${rec.id}"
                        SearchResultCard(
                            record = rec,
                            bookmarked = flagged[key] ?: rec.bookmarked,
                            onToggleBookmark = {
                                val next = !(flagged[key] ?: rec.bookmarked)
                                flagged[key] = next                      // optimistic
                                scope.launch {
                                    val ok = runCatching {
                                        api.pushBookmark(baseUrl, rec.ecode, rec.id, next)
                                    }.getOrDefault(false)
                                    if (!ok) {
                                        flagged[key] = !next             // put it back; nothing was saved
                                        message = "Couldn't reach the server — not flagged"
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Says WHICH failure happened, because the fixes are unrelated.
 *
 * Written after a real diagnosis went the wrong way: search returned "check the connection and the
 * sign-in", so the hunt started in Settings, when the actual cause was a server still running an
 * older build that had no such endpoint — a 404. A message that names the status points at the
 * right thing straight away.
 */
private fun describeFailure(e: Throwable, baseUrl: String): String = when {
    e is NibrasApi.ApiException && e.status == 404 ->
        "This server has no search endpoint (404). It's probably running an older build — " +
            "deploy the current WAR to $baseUrl and restart it."
    e is NibrasApi.ApiException && e.status == 401 ->
        "The server rejected the sign-in (401). Check the username and password in Settings."
    e is NibrasApi.ApiException && e.status == 403 ->
        "Signed in, but not allowed to search (403). This account needs the content.write permission."
    e is NibrasApi.ApiException && (e.status ?: 0) >= 500 ->
        "The server errored (${e.status}). Check its log."
    e is NibrasApi.ApiException && e.status != null ->
        "The server returned HTTP ${e.status}."
    // No status at all: the request never got a reply.
    else -> "Couldn't reach $baseUrl — check the address in Settings and that the server is running."
}

/** One server record: the fields worth seeing on a phone, plus the flag button. */
@Composable
private fun SearchResultCard(
    record: NibrasRecord,
    bookmarked: Boolean,
    onToggleBookmark: () -> Unit,
) {
    val preview = remember(record.description) {
        htmlToPlain(record.description).replace(Regex("\\s+"), " ").trim().take(200)
    }
    Card(colors = CardDefaults.cardColors(), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(10.dp)) {
            // Module code as a leading badge — the same letter the rail uses, so a mixed-type
            // result list stays readable at a glance.
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    record.ecode,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    record.summary.ifBlank { "(untitled)" },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (preview.isNotBlank()) {
                    Text(
                        preview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val meta = listOfNotNull(
                    record.subtype.ifBlank { null },
                    record.context.ifBlank { null },
                    record.date.ifBlank { null },
                    "#${record.id}",
                ).joinToString(" · ")
                Text(
                    meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            IconButton(onClick = onToggleBookmark) {
                Icon(
                    if (bookmarked) Icons.Filled.BookmarkAdded else Icons.Filled.BookmarkAdd,
                    contentDescription = if (bookmarked) "Flagged — will sync to this phone"
                    else "Flag to sync to this phone",
                    tint = if (bookmarked) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
