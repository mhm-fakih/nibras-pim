package com.knotes.ui

import android.content.Context
import android.content.Intent
import android.os.Environment
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import com.knotes.FolderLayout
import com.knotes.R
import com.knotes.nibras.NibrasRecord
import com.knotes.nibras.RESOURCES_CODE
import com.knotes.player.isMediaFile
import com.knotes.util.FileNames
import java.io.File

/**
 * Where a server record's files are on this phone: the root of the phone's copy of repository 1
 * (Syncthing keeps it in sync) plus each module's folder layout, as the server's Settings → modules
 * page configures it and /sync/clientTaxonomy reports it (see [FolderLayout]).
 */
data class RecordFolders(val root: String, val layouts: Map<String, FolderLayout>) {
    fun layoutOf(code: String): FolderLayout = layouts[code] ?: FolderLayout.defaultFor(code)
}

/** The code the server nests type-bucketed folders under: the resource type for R, else the subtype. */
private val NibrasRecord.typeCode: String
    get() = if (type == RESOURCES_CODE) resourceType else subtype

/**
 * A backend record's files, read from the phone's own copy of its folder — not from the server's
 * `files_list` column. Nothing on the backend keeps that column up to date, so a file dropped into
 * the repository folder never reached it and the record showed no files. Visible regular files
 * only, by name; an absent folder is simply no files.
 */
fun recordFiles(folders: RecordFolders, record: NibrasRecord): List<String> {
    return recordDir(folders, record).listFiles()
        ?.filter { it.isFile && !it.name.startsWith(".") }
        ?.map { it.name }
        ?.sortedBy { it.lowercase() }
        .orEmpty()
}

/**
 * The folder holding a record's files, built the way the backend's RepositoryService.folder builds
 * it: `<CODE>/[<typeCode>/][<id/100>/]<id>`, each level only when the module's layout has it.
 *
 * When that folder isn't there the other spellings are tried — a layout cached before the server
 * changed it, or a type-bucketed record whose type code the export didn't carry (search hits give
 * the type's name, not its code) — so the files still show up; with none present, the configured
 * path is returned.
 */
fun recordDir(folders: RecordFolders, record: NibrasRecord): File {
    val layout = folders.layoutOf(record.type)
    val id = record.id
    val bucket = id.toLongOrNull()?.div(100)?.toString()
    val base = File(folders.root, record.type)
    fun under(dir: File, by100: Boolean): File? =
        if (by100) bucket?.let { File(File(dir, it), id) } else File(dir, id)
    val typeDir = record.typeCode.trim().takeIf { layout.byType && it.isNotBlank() }?.let { File(base, it) }
    val configured = under(typeDir ?: base, layout.by100) ?: File(base, id)
    if (configured.isDirectory) return configured
    val others = buildList {
        add(under(base, !layout.by100))
        add(under(base, layout.by100))
        // Every type subfolder, when the record's own type code is unknown or out of date.
        base.listFiles()?.filter { it.isDirectory && it.name.toLongOrNull() == null }?.forEach {
            add(under(it, true)); add(under(it, false))
        }
    }
    return others.firstOrNull { it != null && it.isDirectory } ?: configured
}

fun openRecordFile(
    context: Context,
    folders: RecordFolders,
    record: NibrasRecord,
    filename: String,
    onPlayMedia: (File) -> Unit,
) {
    val dir = recordDir(folders, record)
    // The name comes from the server's files_list column while the bytes come from Syncthing, so
    // for a non-ASCII (Arabic) name the two spellings can differ by bidi marks or Unicode
    // normalization without differing to a reader. Resolve tolerantly — see FileNames.
    val file = FileNames.resolve(dir, filename)
    if (file == null) {
        // Name the neighbours: "not found" on a folder that visibly contains the file is the most
        // confusing failure this screen can produce, and it only ever happens on non-ASCII names.
        val siblings = dir.listFiles()?.joinToString(", ") { it.name }.orEmpty()
        val detail = if (siblings.isBlank()) context.getString(R.string.notes_open_folder_empty_or_missing)
        else context.getString(R.string.notes_open_folder_has, siblings)
        Toast.makeText(context, context.getString(R.string.notes_open_not_found_in, filename, dir.path, detail), Toast.LENGTH_LONG).show()
        return
    }
    if (isMediaFile(file.name)) {
        onPlayMedia(file)
        return
    }
    openLocalFile(context, file)
}

/** Opens any on-disk file in another app via a FileProvider content URI (note attachments etc.). */
fun openLocalFile(context: Context, file: File) {
    if (!file.isFile) {
        Toast.makeText(context, context.getString(R.string.notes_open_not_found, file.absolutePath), Toast.LENGTH_LONG).show()
        return
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    // Extension → MIME, with a text/plain net for the common unmapped text suffixes (.md etc.) —
    // an unmapped type used to become "*/*", which nothing resolves directly.
    // FileNames.ext, not File.extension: a trailing bidi mark on an Arabic name would otherwise
    // make every extension unrecognised, collapsing the type to "*/*".
    val ext = FileNames.ext(file.name)
    val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        ?: when (ext) {
            "md", "txt", "log", "srt", "json", "csv" -> "text/plain"
            else -> "*/*"
        }
    // ALWAYS via the app chooser: knotes itself is a registered handler for audio (its own
    // player), so a plain ACTION_VIEW on an mp3 silently resolved back into knotes and looked
    // like a no-op. The chooser always asks — pick any app, including knotes deliberately.
    val view = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(Intent.createChooser(view, file.name)) }
        .onFailure { e ->
            Toast.makeText(context, context.getString(R.string.notes_open_cannot_open, file.name, e.message.orEmpty()), Toast.LENGTH_LONG).show()
        }
}

/** Hands [file] to the share sheet (messaging apps, Bluetooth, Drive…) via a FileProvider URI. */
fun shareLocalFile(context: Context, file: File) {
    if (!file.isFile) {
        Toast.makeText(context, context.getString(R.string.notes_open_not_found, file.absolutePath), Toast.LENGTH_LONG).show()
        return
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(FileNames.ext(file.name)) ?: "*/*"
    val send = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(Intent.createChooser(send, file.name)) }
        .onFailure { e ->
            Toast.makeText(context, context.getString(R.string.notes_open_cannot_open, file.name, e.message.orEmpty()), Toast.LENGTH_LONG).show()
        }
}

/**
 * Opens a folder in the device's file manager. Folder-opening has no standard Android intent, so
 * this tries, in order: the documents-UI directory URI (resolves to My Files / Files on this
 * device), then a FileProvider URI with the quasi-standard "resource/folder" type.
 */
fun openFolder(context: Context, dir: File) {
    if (!dir.isDirectory) {
        Toast.makeText(context, context.getString(R.string.notes_open_no_folder, dir.absolutePath), Toast.LENGTH_LONG).show()
        return
    }
    // Canonicalise BOTH sides so a /sdcard-configured notes dir still matches the storage root.
    val canonical = runCatching { dir.canonicalFile }.getOrDefault(dir)
    val root = runCatching { Environment.getExternalStorageDirectory().canonicalFile }.getOrNull()
    val intents = buildList {
        if (root != null && canonical.path.startsWith(root.path)) {
            val rel = canonical.path.removePrefix(root.path).trimStart('/')
            val docUri = DocumentsContract.buildDocumentUri(
                "com.android.externalstorage.documents", "primary:$rel",
            )
            // NO grant flag here: that provider isn't ours, and granting permissions on a URI we
            // don't own throws SecurityException — which is exactly what silently killed this
            // intent before. The file manager reads the directory with its own access.
            add(Intent(Intent.ACTION_VIEW).setDataAndType(docUri, DocumentsContract.Document.MIME_TYPE_DIR))
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", canonical)
        add(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "resource/folder")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)   // ours — granting is legal
            },
        )
    }
    for (intent in intents) {
        if (runCatching { context.startActivity(intent) }.isSuccess) return
    }
    Toast.makeText(context, context.getString(R.string.notes_open_no_file_manager, dir.name), Toast.LENGTH_LONG).show()
}
