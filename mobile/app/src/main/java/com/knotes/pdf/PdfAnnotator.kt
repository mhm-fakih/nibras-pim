package com.knotes.pdf

import android.content.Context
import android.graphics.PointF
import android.graphics.RectF
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDColor
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotation
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationMarkup
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDBorderStyleDictionary
import java.io.File
import java.util.Calendar

/**
 * A mark made in the PDF viewer, in PAGE POINTS with the origin at the top left of the page as
 * it is shown (rotation applied) — the coordinates PdfRenderer draws and selects in.
 */
sealed interface PdfMark {
    val page: Int

    /** A pen stroke. */
    data class Ink(override val page: Int, val points: List<PointF>) : PdfMark

    /** A highlight: one box per text line it covers (or the dragged box, on a page without text). */
    data class Highlight(override val page: Int, val rects: List<RectF>) : PdfMark
}

/**
 * Writes [PdfMark]s into the PDF as standard annotations — Ink for the pen, Highlight for the
 * highlighter — with appearance streams, so Okular, browsers and every other reader show them and
 * can delete them again. The page content itself is never touched.
 *
 * Same safety rules as the image burner: the original is copied into `.scr-backup/` the first
 * time, and the new file is written beside it and renamed over it, so a crash mid-save never
 * leaves half a PDF.
 */
object PdfAnnotator {

    private const val BACKUP_DIR = ".scr-backup"
    private const val AUTHOR = "Nibras"

    val INK_RGB = floatArrayOf(0.86f, 0.12f, 0.12f)
    val HIGHLIGHT_RGB = floatArrayOf(1f, 0.92f, 0.2f)
    const val INK_WIDTH_PT = 1.6f

    fun save(context: Context, file: File, marks: List<PdfMark>): Result<Unit> = runCatching {
        if (marks.isEmpty()) return@runCatching
        PDFBoxResourceLoader.init(context.applicationContext)
        backup(file)
        val tmp = File(file.parentFile, file.name + ".scr-tmp")
        PDDocument.load(file, MemoryUsageSetting.setupMixed(32L shl 20)).use { doc ->
            // An owner-password PDF opens for reading but won't save with its old encryption.
            if (doc.isEncrypted) doc.isAllSecurityToBeRemoved = true
            for ((index, pageMarks) in marks.groupBy { it.page }) {
                if (index !in 0 until doc.numberOfPages) continue
                val page = doc.getPage(index)
                val map = PageMap(page)
                val annots = page.annotations.toMutableList()
                for (m in pageMarks) annots += annotation(m, map, page).also { it.constructAppearances(doc) }
                page.annotations = annots
            }
            doc.save(tmp)
        }
        if (!tmp.renameTo(file)) {
            tmp.delete()
            error("could not replace ${file.name}")
        }
    }

    private fun annotation(m: PdfMark, map: PageMap, page: PDPage): PDAnnotation {
        val now = Calendar.getInstance()
        val a: PDAnnotationMarkup = when (m) {
            is PdfMark.Ink -> PDAnnotationMarkup().apply {
                cosObject.setName(COSName.SUBTYPE, "Ink")
                val pts = FloatArray(m.points.size * 2)
                m.points.forEachIndexed { i, p ->
                    val (x, y) = map.toPdf(p.x, p.y)
                    pts[2 * i] = x
                    pts[2 * i + 1] = y
                }
                inkList = arrayOf(pts)
                color = PDColor(INK_RGB, PDDeviceRGB.INSTANCE)
                borderStyle = PDBorderStyleDictionary().apply { width = INK_WIDTH_PT }
                rectangle = bounds(pts, INK_WIDTH_PT * 2)
            }
            is PdfMark.Highlight -> PDAnnotationTextMarkup(PDAnnotationTextMarkup.SUB_TYPE_HIGHLIGHT).apply {
                // Quad order the readers expect: top-left, top-right, bottom-left, bottom-right.
                val quads = FloatArray(m.rects.size * 8)
                m.rects.forEachIndexed { i, r ->
                    val corners = listOf(r.left to r.top, r.right to r.top, r.left to r.bottom, r.right to r.bottom)
                    corners.forEachIndexed { c, (x, y) ->
                        val (px, py) = map.toPdf(x, y)
                        quads[i * 8 + c * 2] = px
                        quads[i * 8 + c * 2 + 1] = py
                    }
                }
                quadPoints = quads
                color = PDColor(HIGHLIGHT_RGB, PDDeviceRGB.INSTANCE)
                rectangle = bounds(quads, 1f)
            }
        }
        a.page = page
        a.isPrinted = true
        a.titlePopup = AUTHOR
        a.creationDate = now
        a.setModifiedDate(now)
        return a
    }

    private fun bounds(xy: FloatArray, pad: Float): PDRectangle {
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for (i in xy.indices step 2) {
            minX = minOf(minX, xy[i]); maxX = maxOf(maxX, xy[i])
            minY = minOf(minY, xy[i + 1]); maxY = maxOf(maxY, xy[i + 1])
        }
        return PDRectangle(minX - pad, minY - pad, (maxX - minX) + 2 * pad, (maxY - minY) + 2 * pad)
    }

    /**
     * Shown-page points (top-left origin, rotation applied) → PDF user space (bottom-left origin,
     * unrotated, offset by the crop box). A page with /Rotate is drawn turned, so its x and y
     * swap or flip on the way back.
     */
    private class PageMap(page: PDPage) {
        private val box = page.cropBox
        private val rotation = ((page.rotation % 360) + 360) % 360

        fun toPdf(u: Float, v: Float): Pair<Float, Float> = when (rotation) {
            90 -> (box.lowerLeftX + v) to (box.lowerLeftY + u)
            180 -> (box.upperRightX - u) to (box.lowerLeftY + v)
            270 -> (box.upperRightX - v) to (box.upperRightY - u)
            else -> (box.lowerLeftX + u) to (box.upperRightY - v)
        }
    }

    private fun backup(file: File) {
        val dir = File(file.parentFile, BACKUP_DIR)
        val target = File(dir, file.name)
        if (target.exists()) return
        dir.mkdirs()
        file.copyTo(target)
    }
}
