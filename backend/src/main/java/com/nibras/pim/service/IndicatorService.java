package com.nibras.pim.service;

import com.nibras.pim.domain.Goal;
import com.nibras.pim.domain.Indicator;
import com.nibras.pim.repo.IndicatorRepository;
import com.nibras.pim.web.dto.IndicatorDto;
import com.nibras.pim.web.dto.LookupDto;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** Indicator as a first-class module (definitions). Data points live in indicator_data. */
@Service
public class IndicatorService extends AbstractCrudService<Indicator, IndicatorDto> {

    public IndicatorService(IndicatorRepository repo) {
        super(repo);
    }

    @Override
    public String name() {
        return "indicators";
    }

    @Override
    protected List<String> searchFields() {
        return List.of("name", "code", "description", "summary");
    }

    /** Distinct, saturated line colours (d3 category10) for overlaying indicator series. */
    private static final String[] CHART_PALETTE = {
            "#1f77b4", "#ff7f0e", "#2ca02c", "#d62728", "#9467bd",
            "#8c564b", "#e377c2", "#7f7f7f", "#bcbd22", "#17becf",
    };

    /** Deterministic palette colour from a key (name/code), so a blank colour is stable per indicator. */
    private static String paletteColor(String key) {
        String s = key == null || key.isBlank() ? "?" : key;
        int h = 0;
        for (int i = 0; i < s.length(); i++) {
            h = (h + s.charAt(i)) % CHART_PALETTE.length;
        }
        return CHART_PALETTE[h];
    }

    @Override
    protected IndicatorDto toDto(Indicator i) {
        String color = i.getColor() != null && !i.getColor().isBlank()
                ? i.getColor() : paletteColor(i.feedTitle());
        return new IndicatorDto(i.getId(), i.getVersion(), i.feedTitle(),
                i.getCode(), i.getName(), i.getDescription(), i.getFrequency(), i.getScale(),
                i.getQuery(), i.getPath(), i.getExtensions(), i.getScript(),
                i.getCategory(), color, i.getPriority(),
                i.getBookmarked(), i.getAutoCollect(),
                i.getGoal() == null ? null : i.getGoal().getId(),
                i.getGoal() == null ? null : goalLookup(i.getGoal()),
                i.getSummary(), i.getNotes(),
                i.getStartDate(), i.getEndDate(),
                coverExists(i.getId()), i.getDateCreated(), i.getLastUpdated());
    }

    @Override
    protected Indicator toNew(IndicatorDto dto) {
        Indicator i = new Indicator();
        apply(dto, i);
        return i;
    }

    @Override
    protected void apply(IndicatorDto dto, Indicator i) {
        i.setCode(dto.code());
        i.setName(dto.name());
        i.setDescription(dto.description());
        i.setFrequency(dto.frequency());
        i.setScale(dto.scale());
        i.setQuery(dto.query());
        i.setPath(dto.path());
        i.setExtensions(dto.extensions());
        i.setScript(dto.script());
        i.setCategory(dto.category());
        // Persist the colour, filling a deterministic palette value when the user left it blank.
        i.setColor(dto.color() != null && !dto.color().isBlank() ? dto.color() : paletteColor(
                dto.name() != null && !dto.name().isBlank() ? dto.name() : dto.code()));
        i.setPriority(dto.priority());
        i.setBookmarked(dto.bookmarked());
        i.setAutoCollect(dto.autoCollect());
        i.setGoal(ref(Goal.class, dto.goalId()));
        i.setSummary(dto.summary());
        i.setNotes(dto.notes());
        i.setStartDate(dto.startDate());
        i.setEndDate(dto.endDate());
    }

    @Override
    public Map<String, List<LookupDto>> lookups() {
        return Map.of("goals", bookmarkedGoalOptions());
    }
}
