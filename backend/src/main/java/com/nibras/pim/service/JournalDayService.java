package com.nibras.pim.service;

import com.nibras.pim.domain.Journal;
import com.nibras.pim.repo.JournalRepository;
import com.nibras.pim.repo.JournalTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nibras.pim.domain.lookup.JournalType;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

/**
 * Enforces "one and only one summary-of-the-day journal" (type {@code smm}) per calendar day, and
 * keeps its repo-1 folder symlinks in sync. Triggered by clicking a day in the mini-calendar.
 */
@Service
public class JournalDayService {

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");
    /** Type code (and name) of the day's board journal that Quick board appends to. */
    public static final String BOARD = "board";

    private final JournalRepository journals;
    private final JournalTypeRepository journalTypes;
    private final RepositoryService repos;
    private final LookupAdminService lookups;
    private final JournalService journalService;

    public JournalDayService(JournalRepository journals, JournalTypeRepository journalTypes,
                             RepositoryService repos, LookupAdminService lookups, JournalService journalService) {
        this.journals = journals;
        this.journalTypes = journalTypes;
        this.repos = repos;
        this.lookups = lookups;
        this.journalService = journalService;
    }

    public record BoardAppend(Long id, String summary, boolean created, boolean typeCreated) {
    }

    /**
     * Quick board: add {@code text} to the day's {@code board} journal, under a {@code [HH:mm]}
     * stamp. The entry is created on the first jot of the day, and the type itself the first time
     * ever, so the form works on a fresh database. Later jots go below the earlier ones, one blank
     * line apart, so the entry reads as the day's log in order.
     */
    @Transactional
    public BoardAppend appendToBoard(String text, LocalDateTime now) {
        if (text == null || text.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Nothing to add.");
        }
        boolean typeCreated = false;
        JournalType type = journalTypes.findFirstByCode(BOARD).orElse(null);
        if (type == null) {
            lookups.create("journal_type", "Board", BOARD);
            type = journalTypes.findFirstByCode(BOARD).orElseThrow();
            typeCreated = true;
        }
        LocalDate day = now.toLocalDate();
        List<Journal> existing = journals.findOfTypeOnDay(BOARD, day.atStartOfDay(), day.plusDays(1).atStartOfDay());
        String entry = "[" + now.format(HM) + "] " + text.strip().replace("\r\n", "\n");
        boolean created = existing.isEmpty();
        Journal j;
        if (created) {
            j = new Journal();
            j.setType(type);
            j.setStartDate(day.atTime(12, 0));         // noon, as the day's smm: unambiguously on the day
            j.setSummary("Board " + day.format(DMY));
            j.setBookmarked(false);
            j.setDescription(entry);
        } else {
            j = existing.get(0);
            String before = j.getDescription() == null ? "" : j.getDescription().stripTrailing();
            j.setDescription(before.isEmpty() ? entry : before + "\n\n" + entry);
        }
        j = journals.saveAndFlush(j);
        journalService.reindexRecord(j.getId());
        return new BoardAppend(j.getId(), j.getSummary(), created, typeCreated);
    }

    public record DayJournal(String module, Long id, boolean created) {
    }

    /** Get (or create) the day's smm journal, then ensure its repo-1 symlinks exist. */
    @Transactional
    public DayJournal getOrCreate(LocalDate date) {
        List<Journal> existing = journals.findSmmOnDay(date.atStartOfDay(), date.plusDays(1).atStartOfDay());
        boolean created = false;
        Journal j;
        if (!existing.isEmpty()) {
            j = existing.get(0);
        } else {
            j = new Journal();
            journalTypes.findFirstByCode("smm").ifPresent(j::setType);
            j.setStartDate(date.atTime(12, 0));         // noon — unambiguously on the day
            j.setSummary("ملخص اليوم " + date.format(DMY));
            j.setBookmarked(false);
            journals.save(j);
            created = true;
        }
        repos.linkJournalDay(date, j.getId());
        return new DayJournal("journals", j.getId(), created);
    }
}
