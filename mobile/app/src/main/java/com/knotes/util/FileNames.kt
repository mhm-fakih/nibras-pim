package com.knotes.util

import java.io.File
import java.text.Normalizer
import java.util.Locale

/**
 * Filename handling for the non-ASCII (in practice: Arabic) names that reach the app from three
 * different producers — the server's `files_list` column, Syncthing's copy of the on-disk tree, and
 * whatever desktop tool originally created the file. Byte-for-byte equality between those three is
 * not guaranteed for non-ASCII names, and every difference below has been observed in real trees:
 *
 *  - **Bidi control marks.** Copying an Arabic name out of an RTL context carries invisible
 *    U+200E/U+200F/U+202A-U+202E/U+2066-U+2069 along with it. They survive into filenames and into
 *    the database, and they land *after* the extension as often as inside the name — so
 *    `name.pdf‏` has extension `pdf‏`, matches no MIME type and no media extension.
 *  - **Unicode presentation forms.** Text that has been through an older Arabic renderer or a PDF
 *    extractor comes back spelled in the U+FB50-U+FEFF presentation block — the lam-alef ligature
 *    U+FEFB in place of the two letters U+0644 U+0627, isolated/initial/medial/final variants in
 *    place of the plain letters. Same word on screen, different bytes on disk. These are
 *    *compatibility* equivalences, so NFKC folds them and NFC does not (Arabic has essentially no
 *    canonical composition — NFC on harakat is a no-op).
 *  - **Locale-sensitive case folding.** `String.lowercase()` uses the default locale; on a Turkish
 *    device `"MP3".lowercase()` is `"mp3"` but `"I"` becomes `"ı"`. Extension matching must be
 *    locale-independent.
 *
 * ASCII names are unaffected by all of this, which is why the symptom only ever showed up on
 * Arabic files.
 */
object FileNames {

    /** Invisible bidi formatting characters; legal in a filename, meaningless for matching. */
    private val BIDI = Regex("[\\u200E\\u200F\\u202A-\\u202E\\u2066-\\u2069\\uFEFF]")

    /** Strips invisible bidi marks. Use before any extension or equality test on a filename. */
    fun clean(name: String): String = BIDI.replace(name, "")

    /**
     * The lowercase extension, bidi-stripped and locale-independent. Returns "" when there is none.
     */
    fun ext(name: String): String = clean(name).substringAfterLast('.', "").lowercase(Locale.ROOT)

    /**
     * A comparison key: bidi marks removed, NFKC-normalized, trimmed, case-folded. Two names with
     * the same key are the same file to a reader. Only ever used as a fallback after an exact
     * lookup has missed, so the extra tolerance of NFKC over NFC costs nothing.
     */
    fun matchKey(name: String): String =
        Normalizer.normalize(clean(name), Normalizer.Form.NFKC).trim().lowercase(Locale.ROOT)

    /**
     * Resolves [name] inside [dir], falling back to a [matchKey] scan of the directory when the
     * exact name is absent. The fallback returns the real [File] as the filesystem spells it, which
     * is what every downstream open/read needs — resolving to the requested spelling would produce
     * a path that does not exist.
     *
     * Returns null when nothing in the directory matches.
     */
    fun resolve(dir: File, name: String): File? {
        val exact = File(dir, name)
        if (exact.exists()) return exact
        val wanted = matchKey(name)
        return dir.listFiles()?.firstOrNull { matchKey(it.name) == wanted }
    }
}
