package com.knotes.ui

import android.content.Context
import android.content.Intent
import android.os.Environment
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import com.knotes.player.isMediaFile
import java.io.File

/**
 * Opens a record's file. Audio/video files are handed to [onPlayMedia], which loads them into the
 * persistent in-app player (so playback survives navigation); everything else is handed to another
 * app via a FileProvider content URI. Files live at `filesDir/TYPE/bucket/id/filename` (Syncthing
 * keeps that tree in sync), matching the Ionic app. [type] may be a nested path, e.g.
 * `R/<resourceType>` for Resources records.
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
    val file = File("$filesDir/$type/$bucket/$id/$filename")
    if (!file.exists()) {
        Toast.makeText(context, "Not found: ${file.absolutePath}", Toast.LENGTH_LONG).show()
        return
    }
    if (isMediaFile(filename)) {
        onPlayMedia(file)
        return
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(intent) }
        .onFailure { Toast.makeText(context, "No app to open ${file.name}", Toast.LENGTH_LONG).show() }
}

/** Opens any on-disk file in another app via a FileProvider content URI (note attachments etc.). */
fun openLocalFile(context: Context, file: File) {
    if (!file.isFile) {
        Toast.makeText(context, "Not found: ${file.absolutePath}", Toast.LENGTH_LONG).show()
        return
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(intent) }
        .onFailure { Toast.makeText(context, "No app to open ${file.name}", Toast.LENGTH_LONG).show() }
}

/**
 * Opens a folder in the device's file manager. Folder-opening has no standard Android intent, so
 * this tries, in order: the documents-UI directory URI (AOSP/Google Files and most stock managers),
 * then a FileProvider URI with the quasi-standard "resource/folder" type (third-party managers).
 */
fun openFolder(context: Context, dir: File) {
    if (!dir.isDirectory) {
        Toast.makeText(context, "No folder: ${dir.absolutePath}", Toast.LENGTH_LONG).show()
        return
    }
    val intents = buildList {
        val ext = Environment.getExternalStorageDirectory().absolutePath
        if (dir.absolutePath.startsWith(ext)) {
            val rel = dir.absolutePath.removePrefix(ext).trimStart('/')
            val docUri = DocumentsContract.buildDocumentUri(
                "com.android.externalstorage.documents", "primary:$rel",
            )
            add(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(docUri, DocumentsContract.Document.MIME_TYPE_DIR)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
            )
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", dir)
        add(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "resource/folder")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
        )
    }
    for (intent in intents) {
        if (runCatching { context.startActivity(intent) }.isSuccess) return
    }
    Toast.makeText(context, "No file manager can open ${dir.name}", Toast.LENGTH_LONG).show()
}
