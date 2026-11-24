package com.nibras.pim.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.server.ResponseStatusException;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MediaLinkAndMarksTest {

    @TempDir
    Path tmp;

    private static Map<String, String> query(String link) {
        Map<String, String> q = new HashMap<>();
        for (String kv : link.substring(link.indexOf('?') + 1).split("&")) {
            String[] p = kv.split("=", 2);
            q.put(p[0], URLDecoder.decode(p[1], StandardCharsets.UTF_8));
        }
        return q;
    }

    @Test
    void aSignedLinkOpensItsFileOnlyUnchanged() throws Exception {
        Path f = Files.writeString(tmp.resolve("a b.mp4"), "x");
        MediaLinkService links = new MediaLinkService("secret-secret-secret-secret-secret-12");
        String link = links.link("file", f);
        Map<String, String> q = query(link);
        long e = Long.parseLong(q.get("e"));
        assertEquals(f, links.verify("file", q.get("p"), e, q.get("s")));
        // Another kind, another file, a later expiry or an old one: refused.
        assertThrows(ResponseStatusException.class, () -> links.verify("thumb", q.get("p"), e, q.get("s")));
        assertThrows(ResponseStatusException.class, () -> links.verify("file", tmp.resolve("other").toString(), e, q.get("s")));
        assertThrows(ResponseStatusException.class, () -> links.verify("file", q.get("p"), e + 1, q.get("s")));
        long past = Instant.now().getEpochSecond() - 5;
        assertThrows(ResponseStatusException.class,
                () -> links.verify("file", q.get("p"), past, links.sign("file", q.get("p"), past)));
    }

    @Test
    void marksReadAndWriteAsThePhoneDoes() {
        assertEquals(new VideoService.Mark(55_000, "p2"), VideoService.parse("[0:55] p2"));
        assertEquals(new VideoService.Mark(3_723_500, ""), VideoService.parse("﻿[1:02:03.5]"));
        assertEquals(298_000, VideoService.parse("[00004:58.00] x").ms());
        assertNull(VideoService.parse("a transcript line"));
        assertEquals("[1:02:03] intro", VideoService.line(new VideoService.Mark(3_723_900, "intro")));
        assertEquals("[0:07]", VideoService.line(new VideoService.Mark(7_000, "")));
    }

    @Test
    void addingAMarkKeepsEveryOtherLine() throws Exception {
        Path media = Files.writeString(tmp.resolve("talk.mp4"), "x");
        Files.writeString(tmp.resolve("talk.tmk"), "[0:55] p2\nsome note\n[0:10] a\n");
        VideoService v = new VideoService(null, null);
        var marks = v.addMark(media, 30_000, "middle");
        assertEquals(3, marks.size());
        assertEquals("[0:10] a\n[0:30] middle\n[0:55] p2\nsome note\n", Files.readString(tmp.resolve("talk.tmk")));
    }
}
