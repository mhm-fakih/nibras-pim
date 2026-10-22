package com.nibras.pim.web;

import com.nibras.pim.service.SettingService;
import com.nibras.pim.service.checklists.ChecklistService;
import com.nibras.pim.service.checklists.ChecklistWeekFile.Week;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** The phone's daily/weekly checklists, read-only — for the checklist dashboard page. */
@RestController
@RequestMapping("/api/checklists")
public class ChecklistsController {

    /** Minutes between the dashboard's full reloads; 0 turns them off. */
    public static final String RELOAD_KEY = "checklists.dashboard.reloadMinutes";
    public static final int DEFAULT_RELOAD_MINUTES = 4;

    private final ChecklistService service;
    private final SettingService settings;

    public ChecklistsController(ChecklistService service, SettingService settings) {
        this.service = service;
        this.settings = settings;
    }

    /**
     * Which weeks have a file, which week it is now, the folder they are read from, when they last
     * changed, and how often the dashboard reloads itself.
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
                "weeks", service.weeks(),
                "current", service.currentWeek(),
                "dir", service.dir().toString(),
                "changed", service.lastChanged(),
                "reloadMinutes", reload);
    }

    @GetMapping("/{id}")
    public Week week(@PathVariable String id) {
        return service.week(id);
    }
}
