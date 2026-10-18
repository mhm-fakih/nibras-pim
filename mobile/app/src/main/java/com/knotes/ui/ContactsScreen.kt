package com.knotes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.knotes.R
import com.knotes.nibras.ContactTask
import com.knotes.nibras.ContactWithTasks
import com.knotes.flash.FlashCache
import com.knotes.util.FriendlyTime
import com.knotes.util.autoDirection
import com.knotes.util.grouped
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * Bookmarked people, each heading followed by the work still open with them.
 *
 * The page answers one question — "what do I owe this person?" — which is why it groups by contact
 * rather than listing tasks with a contact column. Tasks are tagged by contact on the backend
 * (`task_contact`, alongside tags), and only ACTIVE ones appear: done, cancelled, deferred and
 * frozen are off the list, and a task with no status at all is still on it, since unset is not the
 * same as finished.
 *
 * A contact with nothing open is still shown. An empty heading is the useful answer to "is there
 * anything outstanding with them?" — dropping it would make the page silently incomplete.
 *
 * **Works with no server**, like the flash cards and highlights next door: the saved copy is shown
 * FIRST and the network is only a refresh on top of it. Worth more here than it looks — the page
 * went blank with "Failed to connect" simply because it was opened while the backend was
 * restarting, and "what do I owe this person" is a question that does not stop being useful
 * because a deploy is in progress.
 */
@Composable
fun ContactsScreen(baseUrl: String) {
    val context = LocalContext.current
    val api = remember { com.knotes.nibras.NibrasApi() }
    val cache = remember { FlashCache(context) }
    var people by remember { mutableStateOf<List<ContactWithTasks>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var offlineSince by remember { mutableStateOf<Long?>(null) }

    // Keyed on the rail's refresh signal as well as the url, so one button re-fetches this page
    // without it needing a control of its own.
    val tick by com.knotes.nibras.ServerRefresh.tick.collectAsState()
    LaunchedEffect(baseUrl, tick) {
        // The saved copy before the network is tried at all.
        val cached = withContext(Dispatchers.IO) { cache.contacts() }
        if (cached != null && people == null) people = cached

        withContext(Dispatchers.IO) {
            runCatching { api.fetchContactsRaw(baseUrl) }
                .onSuccess { raw ->
                    cache.saveRaw(FlashCache.CONTACTS, raw)
                    val fresh = cache.contacts().orEmpty()
                    withContext(Dispatchers.Main) {
                        people = fresh; error = null; offlineSince = null
                    }
                }
                .onFailure { e ->
                    val savedAt = cache.savedAt(FlashCache.CONTACTS)
                    withContext(Dispatchers.Main) {
                        if (cached == null) {
                            // Nothing saved and nothing reachable — the one case with nothing to show.
                            people = emptyList()
                            error = e.message?.takeIf { m -> m.isNotBlank() }
                                ?: context.getString(R.string.server_unreachable_nothing_saved)
                        } else {
                            offlineSince = savedAt
                        }
                    }
                }
        }
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        val list = people
        if (list == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        val open = list.sumOf { it.tasks.size }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                when {
                    list.isEmpty() -> stringResource(R.string.server_contacts_title)
                    // Named, not hidden: a list that is hours old should say so rather than
                    // quietly presenting itself as current.
                    offlineSince != null ->
                        stringResource(
                            R.string.server_contacts_header_saved_copy,
                            grouped(list.size), grouped(open), FriendlyTime.ago(LocalContext.current, offlineSince!!),
                        )
                    else -> stringResource(R.string.server_contacts_header, grouped(list.size), grouped(open))
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
        HorizontalDivider()

        if (list.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
                Text(
                    error ?: stringResource(R.string.server_contacts_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }

        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp)) {
            items(list, key = { it.id }) { person -> ContactCard(person) }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun ContactCard(person: ContactWithTasks) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 11.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                person.name.ifBlank { stringResource(R.string.server_contacts_unnamed) },
                style = MaterialTheme.typography.titleSmall.autoDirection(),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (person.tasks.isEmpty()) "—" else grouped(person.tasks.size),
                style = MaterialTheme.typography.labelMedium,
                color = if (person.tasks.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }
        // The subtitle is whatever identifies the person BEYOND their name. `summary` is very often
        // the same string as `name` in this table, and a grey copy of the heading directly under
        // the heading is noise — so anything equal to the name is dropped rather than repeated.
        listOf(person.jobTitle, person.summary)
            .filter { it.isNotBlank() && !it.equals(person.name, ignoreCase = true) }
            .joinToString(" · ")
            .takeIf { it.isNotBlank() }
            ?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall.autoDirection(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

        if (person.tasks.isEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.server_contacts_nothing_open),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        Spacer(Modifier.height(7.dp))
        person.tasks.forEach { TaskRow(it) }
    }
}

@Composable
private fun TaskRow(task: ContactTask) {
    val dueFormat = stringResource(R.string.server_contacts_task_due)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Urgency and priority read at a glance; the server has already ordered them this way.
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(7.dp)
                .background(
                    if (task.urgent) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                    RoundedCornerShape(4.dp),
                ),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                task.summary,
                style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            listOfNotNull(
                task.priority?.let { "p$it" },
                task.status.takeIf { it.isNotBlank() },
                task.due.takeIf { it.isNotBlank() }?.let { dueFormat.format(it) },
            ).takeIf { it.isNotEmpty() }?.let {
                Text(
                    it.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
