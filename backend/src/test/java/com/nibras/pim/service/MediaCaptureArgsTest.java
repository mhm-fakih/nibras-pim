package com.nibras.pim.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MediaCaptureArgsTest {

    @Test
    void idsBecomeLinksAndJunkIsRefused() {
        assertEquals(List.of("https://www.youtube.com/watch?v=dQw4w9WgXcQ", "https://youtu.be/abc"),
                MediaCaptureService.links(List.of(" dQw4w9WgXcQ\nhttps://youtu.be/abc ")));
        assertThrows(ResponseStatusException.class, () -> MediaCaptureService.links(List.of("-rf /")));
        assertThrows(ResponseStatusException.class, () -> MediaCaptureService.links(List.of("  ")));
    }

    @Test
    void choicesMapToTheScriptsFlags() {
        var links = List.of("https://youtu.be/x");
        assertEquals(List.of("-m", "", "--", "https://youtu.be/x"), MediaCaptureService.arguments(
                new MediaCaptureService.Request(null, MediaCaptureService.Mode.BOTH, true, false, false, null), links));
        assertEquals(List.of("-m", "talk", "-a", "-V", "-S", "--", "https://youtu.be/x"), MediaCaptureService.arguments(
                new MediaCaptureService.Request(null, MediaCaptureService.Mode.AUDIO, false, true, true, " talk\n"), links));
        assertEquals(List.of("-m", "", "-A", "-E", "--", "https://youtu.be/x"), MediaCaptureService.arguments(
                new MediaCaptureService.Request(null, MediaCaptureService.Mode.VIDEO, true, true, true, ""), links));
    }
}
