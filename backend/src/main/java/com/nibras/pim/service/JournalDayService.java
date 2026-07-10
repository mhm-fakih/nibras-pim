package com.nibras.pim.service;

import com.nibras.pim.domain.Journal;
import com.nibras.pim.repo.JournalRepository;
import com.nibras.pim.repo.JournalTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Enforces "one and only one summary-of-the-day journal" (type {@code smm}) per calendar day, and
 * keeps its repo-1 folder symlinks in sync. Triggered by clicking a day in the mini-calendar.
 */
@Service
public class JournalDayService {

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final JournalRepository journals;
    private final JournalTypeRepository journalTypes;
    private final RepositoryService repos;

    public JournalDayService(JournalRepository journals, JournalTypeRepository journalTypes,
                             RepositoryService repos) {
        this.journals = journals;
        this.journalTypes = journalTypes;
        this.repos = repos;
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
