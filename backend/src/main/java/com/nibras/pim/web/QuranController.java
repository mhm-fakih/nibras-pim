package com.nibras.pim.web;

import com.nibras.pim.service.QuranService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** The Quran exegesis and notes page: surahs, ayat with their notes, search, copy and exports. */
@RestController
@RequestMapping("/api/quran")
public class QuranController {

    private final QuranService service;

    public QuranController(QuranService service) {
        this.service = service;
    }

    @GetMapping
    public QuranService.Overview overview() {
        return service.overview();
    }

    @GetMapping("/count")
    public Map<String, Integer> count() {
        return Map.of("count", service.count());
    }

    @GetMapping("/surahs/{number}")
    public QuranService.Surah surah(@PathVariable int number) {
        return service.surah(number);
    }

    public record SurahEdit(String notes) {
    }

    @PutMapping("/surahs/{number}")
    public void editSurah(@PathVariable int number, @RequestBody SurahEdit body) {
        service.updateSurahNotes(number, body.notes());
    }

    public record AyaEdit(String notes, Boolean bookmarked) {
    }

    @PutMapping("/ayat/{id}")
    public void editAya(@PathVariable long id, @RequestBody AyaEdit body) {
        service.updateAya(id, body.notes(), body.bookmarked());
    }

    public record Bookmark(boolean bookmarked) {
    }

    @PutMapping("/surahs/{number}/bookmark")
    public void bookmarkSurah(@PathVariable int number, @RequestBody Bookmark body) {
        service.bookmarkSurah(number, body.bookmarked());
    }

    @PutMapping("/surahs/{number}/ayat/{aya}/bookmark")
    public void bookmarkAya(@PathVariable int number, @PathVariable int aya, @RequestBody Bookmark body) {
        service.bookmarkAya(number, aya, body.bookmarked());
    }

    /** A note from the phone, appended to the aya's notes ({@code ref} makes a resend harmless). */
    public record Append(String text, String ref, java.time.LocalDateTime at) {
    }

    @PostMapping("/surahs/{number}/ayat/{aya}/notes")
    public Map<String, String> append(@PathVariable int number, @PathVariable int aya, @RequestBody Append body) {
        return java.util.Collections.singletonMap("notes",
                service.appendAyaNote(number, aya, body.text(), body.ref(), body.at()));
    }

    /** Copies (or refreshes) the surahs and ayat from their source records. */
    @PostMapping("/copy")
    public QuranService.CopyResult copy() {
        return service.copy();
    }

    @GetMapping("/search")
    public List<QuranService.Hit> search(@RequestParam String q, @RequestParam(defaultValue = "200") int limit) {
        return service.search(q, limit);
    }

    @GetMapping("/surahs/{number}/export")
    public ResponseEntity<byte[]> export(@PathVariable int number, @RequestParam(defaultValue = "md") String format) {
        QuranService.Export e = service.export(number, format);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(e.fileName(), StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType(e.contentType()))
                .body(e.body());
    }
}
