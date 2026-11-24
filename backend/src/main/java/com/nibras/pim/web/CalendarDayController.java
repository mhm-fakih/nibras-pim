package com.nibras.pim.web;

import com.nibras.pim.service.CalendarDayService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** Days in the Gregorian, Shamsi and Hijri calendars with Iran's official occasions. */
@RestController
@RequestMapping("/api/calendar-days")
public class CalendarDayController {

    private final CalendarDayService service;

    public CalendarDayController(CalendarDayService service) {
        this.service = service;
    }

    public record Range(LocalDate coveredUntil, int hijriOffset, List<CalendarDayService.Day> days) {
    }

    /** {@code hijriOffset} overrides the server's setting (the phone sends its own). */
    @GetMapping
    public Range days(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                      @RequestParam(required = false) Integer hijriOffset) {
        int offset = hijriOffset == null ? service.hijriOffset() : hijriOffset;
        return new Range(service.coveredUntil(), offset, service.days(from, to, offset));
    }
}
