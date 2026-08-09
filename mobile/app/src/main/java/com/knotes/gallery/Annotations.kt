package com.knotes.gallery

import java.io.File

/**
 * The annotation model, ported from the desktop `scr-annotator` so the two agree about what a mark
 * means. Deliberately free of Android drawing types: this file decides *what* the marks are and
 * *what the file is called*, and [AnnotationBurner] decides how to paint them.
 *
 * Marks are stored in IMAGE pixel coordinates, not view coordinates. The canvas letterboxes the
 * image to fit, so view coordinates depend on the device and the rotation; storing image
 * coordinates means a mark drawn on a phone lands in the same place when burnt at full resolution.
 */
sealed interface Mark {
    /** Freehand stroke: the raw points, in image pixels. */
    data class Pen(val points: List<Pair<Float, Float>>) : Mark

    /**
     * Highlighter. The drag sets only the horizontal extent — the band's thickness is fixed, the
     * same way a real highlighter has a fixed nib. Matches the desktop's HL_HEIGHT behaviour, which
     * is what keeps a highlight looking deliberate rather than like a wobbly rectangle.
     */
    data class Highlight(val x0: Float, val x1: Float, val y: Float) : Mark

    /** Crop rectangle. At most one is ever applied — the last one drawn. */
    data class Crop(val x0: Float, val y0: Float, val x1: Float, val y1: Float) : Mark
}

/** Desktop parity: same colours, widths and alpha, so an image looks the same wherever it's marked. */
object AnnotationStyle {
    const val PEN_COLOR = 0xFF21D34D.toInt()      // green (PEN_RGB)
    const val PEN_WIDTH_PX = 3f                   // at image resolution, as on desktop
    const val HL_COLOR = 0xFFFFEB33.toInt()       // yellow (HL_RGB)
    const val HL_ALPHA = 0.35f
    const val HL_HEIGHT_PX = 26f
    const val JPEG_QUALITY = 95
}

/** Normalised (x0,y0,x1,y1) with x0<=x1 and y0<=y1 — a drag may go in any direction. */
fun normalise(x0: Float, y0: Float, x1: Float, y1: Float) =
    listOf(minOf(x0, x1), minOf(y0, y1), maxOf(x0, x1), maxOf(y0, y1))

/** The band a highlight covers: the drag's x extent, the fixed nib height around its y. */
fun Mark.Highlight.box(): List<Float> = listOf(
    minOf(x0, x1), y - AnnotationStyle.HL_HEIGHT_PX / 2,
    maxOf(x0, x1), y + AnnotationStyle.HL_HEIGHT_PX / 2,
)

/**
 * Renaming, ported from the desktop's `relabel`.
 *
 * A file's name is `<stem> <label><ext>`: applying a label REPLACES any previous one rather than
 * appending, and a blank label restores the bare stem. That is what makes relabelling idempotent —
 * label a screenshot "invoice", change your mind, label it "receipt", and you get
 * `Screenshot… receipt.png`, never `Screenshot… invoice receipt.png`.
 *
 * The original stem has to be remembered, because after one rename the file's own name no longer
 * reveals where the stem ended and the label began. [originalStem] is that memory; pass the name
 * the file had when it was first seen.
 */
object Renamer {

    /**
     * Characters that are illegal or troublesome in a filename. Wider than the desktop's set: that
     * one only has to satisfy ext4, while this folder is usually FAT/exFAT-backed shared storage,
     * where `: * ? " < > |` are rejected outright.
     */
    private val UNSAFE = Regex("""[/\\:*?"<>|\x00-\x1F]""")

    /** Filename components are capped at 255 bytes; the desktop uses the same limit. */
    private const val MAX_LABEL = 150

    /**
     * Each unsafe character becomes a SPACE rather than being deleted, then runs of whitespace
     * collapse. Deleting would silently join words — "invoice/receipt" would become
     * "invoicereceipt" — whereas a separator in the label plainly meant a word break.
     */
    fun sanitise(label: String): String =
        UNSAFE.replace(label, " ").replace(Regex("""\s+"""), " ").trim().take(MAX_LABEL)

    /** Splits a name into stem and extension, keeping a multi-dot name intact. */
    fun splitExtension(name: String): Pair<String, String> {
        val dot = name.lastIndexOf('.')
        return if (dot <= 0) name to "" else name.substring(0, dot) to name.substring(dot)
    }

    /**
     * The name [file] should have once labelled. [originalStem] is the stem before any labelling —
     * without it a second label would be appended to the first.
     */
    fun targetName(file: File, originalStem: String, rawLabel: String): String {
        val label = sanitise(rawLabel)
        val ext = splitExtension(file.name).second
        return if (label.isEmpty()) "$originalStem$ext" else "$originalStem $label$ext"
    }

    /**
     * Renames [file], stepping aside if the target is taken (` 2`, ` 3`, …) rather than
     * overwriting — two screenshots can legitimately deserve the same label.
     *
     * @return the file's new location, or the original when nothing needed to change.
     */
    fun rename(file: File, originalStem: String, rawLabel: String): File {
        val target = targetName(file, originalStem, rawLabel)
        if (target == file.name) return file
        val (stem, ext) = splitExtension(target)
        var candidate = File(file.parentFile, target)
        var n = 2
        while (candidate.exists() && candidate.absolutePath != file.absolutePath) {
            candidate = File(file.parentFile, "$stem $n$ext")
            n++
        }
        return if (file.renameTo(candidate)) candidate else file
    }
}
