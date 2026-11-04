package com.nibras.pim.service.expenses;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * Converts amounts between currencies with rates that change over time.
 *
 * <p>A rate says 1 {@code from} = {@code rate} {@code to}, from {@code validFrom} on; the one in
 * force on a date is the latest whose {@code validFrom} is on or before it. A date before the first
 * rate for a pair uses that first rate rather than giving up — an old expense is better counted at
 * the earliest known rate than left out of the total. A rate entered one way converts the other
 * way too (1/rate), and when there is no rate between two currencies at all, one hop through a
 * third currency is tried (EUR → USD → LBP).
 */
public final class FxConverter {

    public record Rate(String from, String to, double rate, LocalDate validFrom) {
    }

    /** Pair "FROM>TO" → its rates, oldest first; inverses included. */
    private final Map<String, List<Rate>> byPair = new HashMap<>();
    private final Set<String> currencies = new HashSet<>();

    public FxConverter(List<Rate> rates) {
        for (Rate r : rates) {
            if (!(r.rate() > 0) || r.from() == null || r.to() == null) continue;
            String f = r.from().toUpperCase();
            String t = r.to().toUpperCase();
            if (f.equals(t)) continue;
            add(new Rate(f, t, r.rate(), r.validFrom()));
            add(new Rate(t, f, 1.0 / r.rate(), r.validFrom()));
            currencies.add(f);
            currencies.add(t);
        }
        byPair.values().forEach(l -> l.sort(Comparator.comparing(Rate::validFrom)));
    }

    private void add(Rate r) {
        byPair.computeIfAbsent(r.from() + ">" + r.to(), k -> new ArrayList<>()).add(r);
    }

    /** The direct (or inverse) rate from → to in force on {@code date}, if that pair has any. */
    private OptionalDouble direct(String from, String to, LocalDate date) {
        List<Rate> list = byPair.get(from + ">" + to);
        if (list == null || list.isEmpty()) return OptionalDouble.empty();
        Rate pick = list.get(0);
        for (Rate r : list) {
            if (date != null && r.validFrom().isAfter(date)) break;
            pick = r;
        }
        return OptionalDouble.of(pick.rate());
    }

    /** How many {@code to} one {@code from} is worth on {@code date}; empty when no path exists. */
    public OptionalDouble rate(String from, String to, LocalDate date) {
        String f = from.toUpperCase();
        String t = to.toUpperCase();
        if (f.equals(t)) return OptionalDouble.of(1.0);
        OptionalDouble d = direct(f, t, date);
        if (d.isPresent()) return d;
        for (String via : currencies) {
            if (via.equals(f) || via.equals(t)) continue;
            OptionalDouble a = direct(f, via, date);
            OptionalDouble b = direct(via, t, date);
            if (a.isPresent() && b.isPresent()) return OptionalDouble.of(a.getAsDouble() * b.getAsDouble());
        }
        return OptionalDouble.empty();
    }

    public OptionalDouble convert(double amount, String from, String to, LocalDate date) {
        OptionalDouble r = rate(from, to, date);
        return r.isPresent() ? OptionalDouble.of(amount * r.getAsDouble()) : OptionalDouble.empty();
    }
}
