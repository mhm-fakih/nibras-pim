package com.nibras.pim.web;

import com.nibras.pim.service.SyncService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Legacy mobile-sync endpoints, ported from the Grails {@code ker.SyncController} so the knotes
 * Android app can point at this backend without changes. Paths and JSON envelopes are kept identical
 * to the originals ({@code /sync/exportJson<CODE>}, {@code /sync/mobilePush*}, {@code /page/heartbeatJson}).
 * The app's base URL is configurable, so only the host/port differs.
 */
@RestController
public class SyncController {

    private final SyncService sync;

    public SyncController(SyncService sync) {
        this.sync = sync;
    }

    /** Connectivity probe — the app's {@code heartbeat()} checks {@code result == "ok"}. */
    @GetMapping("/page/heartbeatJson")
    public Map<String, Object> heartbeat() {
        return Map.of("result", "ok");
    }

    /**
     * Export all bookmarked records of a type (T, P, J, G, N, W, R). Accepts the type either as a
     * path suffix ({@code /sync/exportJsonT}, the legacy Grails action-name style) or as a
     * {@code ?code=T} query param (the form the knotes app sends) — so the app and any legacy caller
     * both work. The {@code id} query param (username) is accepted but ignored — single-user app.
     */
    @GetMapping({"/sync/exportJson", "/sync/exportJson{suffix}"})
    public Map<String, Object> export(@PathVariable(name = "suffix", required = false) String suffix,
                                      @RequestParam(name = "code", required = false) String code,
                                      @RequestParam(name = "id", required = false) String id) {
        String type = (code != null && !code.isBlank()) ? code
                : (suffix != null && !suffix.isBlank()) ? suffix : "";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("result", "ok");
        body.put("data", sync.export(type));
        return body;
    }

    @PostMapping("/sync/mobilePush")
    public Map<String, Object> mobilePush(@RequestBody Map<String, Object> body) {
        return Map.of("result", sync.pushNotes(data(body)) + " processed updates");
    }

    @PostMapping("/sync/mobilePushHighlight")
    public Map<String, Object> mobilePushHighlight(@RequestBody Map<String, Object> body) {
        return Map.of("result", sync.pushHighlights(data(body)) + " processed updates");
    }

    @PostMapping("/sync/mobilePushComplete")
    public Map<String, Object> mobilePushComplete(@RequestBody Map<String, Object> body) {
        return Map.of("result", sync.pushCompletes(data(body)) + " processed updates");
    }

    @PostMapping("/sync/mobilePushCalendar")
    public Map<String, Object> mobilePushCalendar(@RequestBody Map<String, Object> body) {
        List<Long> ids = sync.pushCalendar(data(body));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("result", ids.size() + " calendar entries saved");
        out.put("data", ids);
        return out;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> data(Map<String, Object> body) {
        Object d = body == null ? null : body.get("data");
        return d instanceof List ? (List<Map<String, Object>>) d : List.of();
    }
}
