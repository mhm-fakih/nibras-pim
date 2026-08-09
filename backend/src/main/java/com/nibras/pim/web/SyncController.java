package com.nibras.pim.web;

import com.nibras.pim.service.ClientSyncViewService;
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
    private final ClientSyncViewService clientSyncView;

    public SyncController(SyncService sync, ClientSyncViewService clientSyncView) {
        this.sync = sync;
        this.clientSyncView = clientSyncView;
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

    /**
     * The parameter vocabulary the clients should be working from: enabled modules with their
     * types, and departments of {@code priority >= 2}, in the clients' own order.
     *
     * <p>Served from {@link ClientSyncViewService} — the SAME method behind the Admin page's
     * "synced to clients" list. That is the point: the Admin page exists so drift between the
     * server and a device is visible, which it cannot be if the page and the device apply their own
     * separate filters. Previously the phone fetched {@code /api/departments?bookmarked=true} and
     * sorted modules by {@code sortOrder}, so it was showing a different set from the one the Admin
     * page claimed it had.
     *
     * <p>Under {@code /sync} rather than {@code /api/admin} so an ordinary signed-in device can read
     * it; the admin route needs {@code tools.admin}, which a phone user has no reason to hold.
     */
    @GetMapping("/sync/clientTaxonomy")
    public ClientSyncViewService.ClientSyncView clientTaxonomy() {
        return clientSyncView.view();
    }

    /**
     * Free-text search across the enabled syncable modules, for the phone's backend-search page.
     * Records come back in the same flat shape as {@code exportJson*}, so the phone parses and
     * renders them with what it already has.
     *
     * <p>{@code ecode} (T G P J N W R) narrows to one module; omit it to search them all.
     * Unlike the exports, results are NOT limited to bookmarked records — finding things that
     * aren't on the phone yet is the point.
     */
    @GetMapping("/sync/mobileSearch")
    public Map<String, Object> mobileSearch(@RequestParam String q,
                                            @RequestParam(required = false) String ecode,
                                            @RequestParam(defaultValue = "25") int limit) {
        List<Map<String, Object>> hits = sync.search(q, ecode, limit);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("result", hits.size() + " matches");
        out.put("data", hits);
        return out;
    }

    /**
     * Flag records so the next export carries them to the phone — the write half of mobile search.
     * Envelope {@code {data:[{ecode,id,bookmarked}]}}; {@code bookmarked} defaults to true and may
     * be false to un-flag.
     */
    @PostMapping("/sync/mobilePushBookmark")
    public Map<String, Object> mobilePushBookmark(@RequestBody Map<String, Object> body) {
        return Map.of("result", sync.pushBookmarks(data(body)) + " records flagged");
    }

    /** Create records of any exportable type from the phone (bookmarked=true so they round-trip). */
    @PostMapping("/sync/mobilePushCreate")
    public Map<String, Object> mobilePushCreate(@RequestBody Map<String, Object> body) {
        List<Long> ids = sync.pushCreates(data(body));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("result", ids.size() + " records saved");
        out.put("data", ids);
        return out;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> data(Map<String, Object> body) {
        Object d = body == null ? null : body.get("data");
        return d instanceof List ? (List<Map<String, Object>>) d : List.of();
    }
}
