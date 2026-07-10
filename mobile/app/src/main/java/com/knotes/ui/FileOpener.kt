package com.knotes.ui

import android.content.Context
import android.content.Intent
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
