package com.knotes.ui

import android.content.Context
import android.content.Intent
import android.os.Environment
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import com.knotes.R
import com.knotes.player.isMediaFile
import com.knotes.util.FileNames
import java.io.File

/**
 * Opens a record's file. Audio/video files are handed to [onPlayMedia], which loads them into the
 * persistent in-app player (so playback survives navigation); everything else is handed to another
 * app via [openLocalFile]. Files live at `filesDir/TYPE/bucket/id/filename` (Syncthing keeps that
 * tree in sync), matching the Ionic app. [type] may be a nested path, e.g. `R/<resourceType>` for
 * Resources records.
 */
fun openRecordFile(
    context: Context,
    filesDir: String,
    type: String,
    id: String,
    filename: String,
    onPlayMedia: (File) -> Unit,
) {
    val bucket = id.toLong() / 100
    val dir = File("$filesDir/$type/$bucket/$id")
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
