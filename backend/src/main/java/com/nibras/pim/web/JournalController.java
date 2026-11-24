package com.nibras.pim.web;

import com.nibras.pim.domain.Journal;
import com.nibras.pim.service.JournalDayService;
import com.nibras.pim.service.JournalService;
import com.nibras.pim.web.dto.JournalDto;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/journals")
public class JournalController extends AbstractCrudController<Journal, JournalDto> {

    private final JournalDayService days;

    public JournalController(JournalService service, JournalDayService days) {
        super(service);
        this.days = days;
    }

    public record BoardRequest(String text) {
    }

    /** Quick board: append to today's board journal (created, with its type, if missing). */
    @PostMapping("/board")
    public JournalDayService.BoardAppend board(@RequestBody BoardRequest body) {
        return days.appendToBoard(body.text(), LocalDateTime.now());
    }
}
