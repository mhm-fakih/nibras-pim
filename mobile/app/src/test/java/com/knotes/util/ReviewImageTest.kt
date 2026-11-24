package com.knotes.util

import com.knotes.gallery.AnnotationBurner
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ReviewImageTest {

    private fun tmp(): File = Files.createTempDirectory("review").toFile()

    @Test
    fun annotatedOnThePhoneThenRenamedStaysReviewed() {
        val dir = tmp()
        val old = File(dir, "Screenshot 1.png").apply { writeText("x") }
        File(dir, ".scr-backup").mkdirs()
        File(dir, ".scr-backup/Screenshot 1.png").writeText("orig")
        val new = File(dir, "Screenshot 1 receipt.png")
        old.renameTo(new)
        AnnotationBurner.followRename(old, new)
        assertEquals(Review.Status.REVIEWED, Review.status(new))
    }

    @Test
    fun annotatedAndRenamedOnTheDesktopIsReviewed() {
        val dir = tmp()
        File(dir, ".originals").mkdirs()
        File(dir, ".originals/shot.png").writeText("orig")
        File(dir, ".scr-annotator-log.tsv").writeText(
            "2026-11-07T10:00:00\tshot.png\tshot a.png\n2026-11-07T10:01:00\tshot a.png\tshot b.png\n",
        )
        val f = File(dir, "shot b.png").apply { writeText("x") }
        assertEquals(Review.Status.REVIEWED, Review.status(f))
    }

    @Test
    fun untouchedImageIsPending() {
        val dir = tmp()
        val f = File(dir, "plain.png").apply { writeText("x") }
        assertEquals(Review.Status.PENDING, Review.status(f))
    }
}
