package com.nibras.pim.web;

import com.nibras.pim.service.SettingService;
import com.nibras.pim.service.checklists.ChecklistDbService;
import com.nibras.pim.service.checklists.ChecklistDbService.CategoryRow;
import com.nibras.pim.service.checklists.ChecklistDbService.Changes;
import com.nibras.pim.service.checklists.ChecklistDbService.CheckRow;
import com.nibras.pim.service.checklists.ChecklistDbService.HistoryEntry;
import com.nibras.pim.service.checklists.ChecklistDbService.ItemRow;
import com.nibras.pim.service.checklists.ChecklistDbService.Push;
import com.nibras.pim.service.checklists.ChecklistService;
import com.nibras.pim.service.checklists.ChecklistWeekFile;
import com.nibras.pim.service.checklists.ChecklistWeekFile.Mark;
import com.nibras.pim.service.checklists.ChecklistWeekFile.Week;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * The daily/weekly checklists, kept in the database: the week views the web and the wall dashboard
 * read, the edits the web makes (ticks, notes, categories, items), and the exchange the phone syncs
 * through.
 */
@RestController
@RequestMapping("/api/checklists")
public class ChecklistsController {

    /** Minutes between the dashboard's full reloads; 0 turns them off. */
    public static final String RELOAD_KEY = "checklists.dashboard.reloadMinutes";
    public static final int DEFAULT_RELOAD_MINUTES = 4;

    private final ChecklistDbService db;
    private final ChecklistService files;
    private final SettingService settings;

    public ChecklistsController(ChecklistDbService db, ChecklistService files, SettingService settings) {
        this.db = db;
        this.files = files;
        this.settings = settings;
    }

    /**
     * Which weeks there are, which week it is now, when anything last changed, how often the
     * dashboard reloads itself, who wins a sync conflict, and the folder week files are imported from.
     */
    @GetMapping
    public Map<String, Object> index() {
        int reload;
        try {
            reload = Math.max(0, Integer.parseInt(settings.get(RELOAD_KEY, String.valueOf(DEFAULT_RELOAD_MINUTES)).trim()));
        } catch (NumberFormatException e) {
            reload = DEFAULT_RELOAD_MINUTES;
        }
        return Map.of(
                "weeks", db.weeks(),
                "current", ChecklistWeekFile.idOf(LocalDate.now()),
                "dir", files.dir().toString(),
                "changed", db.lastChanged(),
                "reloadMinutes", reload,
                "winner", db.winner());
    }

    @GetMapping("/{id}")
    public Week week(@PathVariable String id) {
        return db.week(id);
    }

    // ---------------------------------------------------------------- web edits

    public record CheckRequest(String item, LocalDate day, Mark mark, String note) {
    }

    @PutMapping("/checks")
    public CheckRow setCheck(@RequestBody CheckRequest r) {
        return db.setCheck(r.item(), r.day(), r.mark() == null ? Mark.NONE : r.mark(), r.note());
    }

    public record CategoryRequest(String name, Boolean weekly, Boolean active, String week) {
    }

    @PostMapping("/categories")
    public CategoryRow createCategory(@RequestBody CategoryRequest r) {
        return db.createCategory(r.name(), Boolean.TRUE.equals(r.weekly()), r.week());
    }

    @PatchMapping("/categories/{uid}")
    public CategoryRow updateCategory(@PathVariable String uid, @RequestBody CategoryRequest r) {
        return db.updateCategory(uid, r.name(), r.weekly(), r.active(), r.week());
    }

    public record ItemRequest(String category, String name, Boolean active, String week) {
    }

    @PostMapping("/items")
    public ItemRow createItem(@RequestBody ItemRequest r) {
        return db.createItem(r.category(), r.name(), r.week());
    }

    @PatchMapping("/items/{uid}")
    public ItemRow updateItem(@PathVariable String uid, @RequestBody ItemRequest r) {
        return db.updateItem(uid, r.name(), r.active(), r.week());
    }

    @GetMapping("/items/{uid}/history")
    public List<HistoryEntry> history(@PathVariable String uid) {
        return db.history(uid);
    }

    /** Items not on the list in week {@code week} — ended earlier, or starting later — to reopen. */
    @GetMapping("/inactive")
    public List<ItemRow> inactive(@RequestParam String week) {
        return db.inactiveItems(week);
    }

    /** Who wins when the phone and the web changed the same row between two syncs. */
    @PutMapping("/winner")
    public Map<String, String> setWinner(@RequestBody Map<String, String> body) {
        String w = body.get("winner");
        if (!ChecklistDbService.WINNER_MOBILE.equals(w) && !ChecklistDbService.WINNER_WEB.equals(w)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "winner is mobile or web");
        }
        settings.set(ChecklistDbService.WINNER_KEY, w);
        return Map.of("winner", w);
    }

    /** Reads the week files of the notes folder in again, adding only what the database lacks. */
    @PostMapping("/import")
    public Map<String, Integer> importFiles() {
        return Map.of("added", db.importFiles());
    }

    // ---------------------------------------------------------------- phone sync

    @GetMapping("/sync")
    public Changes changes(@RequestParam(defaultValue = "0") long since) {
        return db.changesSince(since);
    }

    @PostMapping("/sync")
    public Changes push(@RequestBody Push push) {
        return db.push(push);
    }
}
