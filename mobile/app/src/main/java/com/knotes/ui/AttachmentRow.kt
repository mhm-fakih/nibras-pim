package com.knotes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.knotes.player.isMediaFile
import com.knotes.util.Bidi
import com.knotes.util.FileKind
import com.knotes.util.fileKind

/**
 * One attached file on a note card: its kind's icon, then its name, on a tinted strip.
 *
 * <p>The name used to be labelSmall grey text behind a paperclip — the same weight as the badges
 * below it, so a card with attachments read as one grey block and the file names, which are the
 * reason to tap, were the least visible thing on it. Here they get body-sized text on their own
 * background, so the strip separates from the note's prose above and from the metadata below
 * without a divider.
 *
 * <p>The icon of a file the app can play is tinted with the accent colour. That is not decoration:
 * tapping a playable file opens the in-app player and tapping anything else throws it out to
 * whatever app claims it — two quite different things to happen, and the tint is the only warning
 * of which one you are about to get.
 */
@Composable
fun AttachmentRow(name: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val playable = isMediaFile(name)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
    ) {
        Icon(
            fileKindIcon(fileKind(name)),
            contentDescription = null,
            tint = if (playable) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        // Directional: an Arabic file name in a left-to-right card lays out backwards otherwise,
        // and these names are long enough that the ellipsis lands at the wrong end too.
        Text(
            text = Bidi.directional(name),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The glyph for a [FileKind]. [FileKind.OTHER] gets the blank-page icon — an honest "a file". */
fun fileKindIcon(kind: FileKind): ImageVector = when (kind) {
    FileKind.AUDIO -> Icons.Filled.AudioFile
    FileKind.VIDEO -> Icons.Filled.VideoFile
    FileKind.IMAGE -> Icons.Filled.Image
    FileKind.PDF -> Icons.Filled.PictureAsPdf
    FileKind.DOC -> Icons.Filled.Description
    FileKind.SHEET -> Icons.Filled.TableChart
    FileKind.SLIDES -> Icons.Filled.Slideshow
    FileKind.TEXT -> Icons.Filled.Notes
    FileKind.ARCHIVE -> Icons.Filled.Archive
    FileKind.CODE -> Icons.Filled.Code
    FileKind.OTHER -> Icons.AutoMirrored.Filled.InsertDriveFile
}
