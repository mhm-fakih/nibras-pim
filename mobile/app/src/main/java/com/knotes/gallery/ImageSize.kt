package com.knotes.gallery

import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import java.io.File

/**
 * The image's upright pixel dimensions, read from the header without decoding any pixels.
 *
 * Needed on every page of the gallery purely to map touches to image coordinates, so decoding the
 * whole bitmap for it would be wasteful — `inJustDecodeBounds` reads a few hundred bytes instead.
 *
 * Returns the size AFTER EXIF rotation, because that is the picture the viewer shows and therefore
 * the one being drawn on: a portrait photo stored as landscape-plus-rotate-90 would otherwise map
 * every touch through a transposed frame.
 */
fun uprightSize(file: File): Pair<Int, Int>? {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, opts)
    if (opts.outWidth <= 0 || opts.outHeight <= 0) return null

    val rotated = runCatching {
        when (ExifInterface(file.absolutePath)
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_ROTATE_270 -> true
            else -> false
        }
    }.getOrDefault(false)

    return if (rotated) opts.outHeight to opts.outWidth else opts.outWidth to opts.outHeight
}
