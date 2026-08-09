package com.knotes.gallery

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.exifinterface.media.ExifInterface
import java.io.File

/**
 * Writes marks into the image file at full resolution — the mobile half of the desktop's
 * `burn_edits`.
 *
 * Three rules are carried over deliberately, because each of them is wrong if reversed:
 *
 *  1. **Crop is applied LAST**, whatever order the marks were drawn in. Every other mark is
 *     positioned in the uncropped image's coordinates, so cropping first would shift them all.
 *  2. **The highlighter is composited, not painted.** It is translucent, so it goes onto its own
 *     layer that is then drawn over the photo; painting it directly would blend it with whatever
 *     had already been drawn rather than with the photo.
 *  3. **The original is backed up first**, into a `.scr-backup` sibling folder. Burning is
 *     destructive and there is no undo once the file is replaced.
 *
 * The write itself is atomic: a temp file, then a rename. A half-written image is never left behind
 * if the process dies mid-save.
 */
object AnnotationBurner {

    private const val BACKUP_DIR = ".scr-backup"
    private const val TMP_SUFFIX = ".scr-tmp"

    /**
     * Decodes [file] upright.
     *
     * EXIF orientation matters here in a way it does not for mere display: the viewer applies the
     * rotation for you, so marks are drawn against the UPRIGHT picture. Decoding without applying
     * it would burn them onto a sideways bitmap — every mark rotated off its target. Returns null
     * when the file isn't a decodable image.
     */
    fun decodeUpright(file: File): Bitmap? {
        val raw = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        val orientation = runCatching {
            ExifInterface(file.absolutePath)
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            else -> return raw
        }
        return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
            .also { if (it != raw) raw.recycle() }
    }

    /** Paints [marks] onto a copy of [source]; crop last. Does not touch any file. */
    fun render(source: Bitmap, marks: List<Mark>): Bitmap {
        val out = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)

        val pen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AnnotationStyle.PEN_COLOR
            style = Paint.Style.STROKE
            strokeWidth = AnnotationStyle.PEN_WIDTH_PX
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        var crop: Mark.Crop? = null
        for (mark in marks) {
            when (mark) {
                is Mark.Pen -> {
                    val pts = mark.points
                    if (pts.size == 1) {
                        // A tap is a dot, not nothing — same as the desktop's single-point ellipse.
                        canvas.drawCircle(
                            pts[0].first, pts[0].second,
                            AnnotationStyle.PEN_WIDTH_PX / 2,
                            Paint(pen).apply { style = Paint.Style.FILL },
                        )
                    } else if (pts.size > 1) {
                        val path = Path().apply {
                            moveTo(pts[0].first, pts[0].second)
                            pts.drop(1).forEach { lineTo(it.first, it.second) }
                        }
                        canvas.drawPath(path, pen)
                    }
                }
                is Mark.Highlight -> {
                    // Its own layer, composited — see rule 2 in the class docs.
                    val (l, t, r, b) = mark.box()
                    val layer = Paint().apply {
                        color = AnnotationStyle.HL_COLOR
                        alpha = (AnnotationStyle.HL_ALPHA * 255).toInt()
                    }
                    canvas.saveLayer(null, null)
                    canvas.drawRect(RectF(l, t, r, b), layer)
                    canvas.restore()
                }
                is Mark.Crop -> crop = mark   // last one wins; applied below
            }
        }

        val c = crop ?: return out
        val (x0, y0, x1, y1) = normalise(c.x0, c.y0, c.x1, c.y1)
        val left = x0.coerceIn(0f, out.width.toFloat()).toInt()
        val top = y0.coerceIn(0f, out.height.toFloat()).toInt()
        val right = x1.coerceIn(0f, out.width.toFloat()).toInt()
        val bottom = y1.coerceIn(0f, out.height.toFloat()).toInt()
        // A stray tap shouldn't reduce the picture to nothing; below 2px it isn't a crop.
        if (right - left < 2 || bottom - top < 2) return out
        return Bitmap.createBitmap(out, left, top, right - left, bottom - top)
            .also { if (it != out) out.recycle() }
    }

    /**
     * Burns [marks] into [file], keeping its format and backing the original up first.
     *
     * @return true when the file was rewritten.
     */
    fun burn(file: File, marks: List<Mark>): Boolean {
        if (marks.isEmpty()) return false
        val source = decodeUpright(file) ?: return false
        val result = render(source, marks)
        if (result != source) source.recycle()

        backup(file)

        val isJpeg = file.extension.lowercase() in setOf("jpg", "jpeg")
        val tmp = File(file.parentFile, file.name + TMP_SUFFIX)
        val ok = runCatching {
            tmp.outputStream().use { out ->
                if (isJpeg) {
                    result.compress(Bitmap.CompressFormat.JPEG, AnnotationStyle.JPEG_QUALITY, out)
                } else {
                    result.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            }
            // Atomic: nothing observes a partially-written image.
            tmp.renameTo(file)
        }.getOrDefault(false)
        if (!ok) tmp.delete()
        result.recycle()
        return ok
    }

    /**
     * Copies the untouched original into a `.scr-backup` sibling folder, once. Later burns of the
     * same file do NOT overwrite that copy — the point is to get back to the photo as it arrived,
     * not to the state before the most recent edit.
     *
     * The folder is dot-prefixed so the gallery's own listing skips it and backups never show up
     * as images to annotate.
     */
    private fun backup(file: File) {
        val dir = File(file.parentFile, BACKUP_DIR)
        val target = File(dir, file.name)
        if (target.exists()) return
        runCatching {
            dir.mkdirs()
            file.copyTo(target, overwrite = false)
        }
    }
}
