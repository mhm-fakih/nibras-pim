package com.nibras.pim.service.expenses;

import com.nibras.pim.service.SettingService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * Expenses on the legacy {@code payment} / {@code payment_category} tables (Grails-era data, so
 * plain JDBC like the other legacy reference tables), in several currencies.
 *
 * <p>Each expense is in its own currency ({@code payment.currency}; NULL = the default currency,
 * which is how everything recorded before currencies existed reads). Totals are in one currency —
 * the default, or one asked for — converting each expense at the rate in force on its date
 * ({@link FxConverter}). An expense whose currency has no path to the total's currency is left out
 * of the sums and reported as such, never silently counted at 1:1.
 */
@Service
public class ExpenseService {

    public static final String DEFAULT_CURRENCY_KEY = "expenses.currency.default";

    private final JdbcTemplate jdbc;
    private final SettingService settings;

    public ExpenseService(JdbcTemplate jdbc, SettingService settings) {
        this.jdbc = jdbc;
        this.settings = settings;
    }

    // ---------------------------------------------------------------- settings & currencies

    public String defaultCurrency() {
        String c = settings.get(DEFAULT_CURRENCY_KEY, "USD");
        return c == null || c.isBlank() ? "USD" : c.trim().toUpperCase();
    }

    @Transactional
    public void setDefaultCurrency(String code) {
        String c = normCode(code);
        requireCurrency(c);
        settings.set(DEFAULT_CURRENCY_KEY, c);
    }

    public record Currency(long id, String code, String name, String symbol) {
    }

    public List<Currency> currencies() {
        return jdbc.query("select id, code, name, symbol from currency order by code",
                (rs, i) -> new Currency(rs.getLong("id"), rs.getString("code"), rs.getString("name"), rs.getString("symbol")));
    }

    @Transactional
    public Currency addCurrency(String code, String name, String symbol) {
        String c = normCode(code);
        Integer n = jdbc.queryForObject("select count(*) from currency where code = ?", Integer.class, c);
        if (n != null && n > 0) throw new ResponseStatusException(CONFLICT, c + " is already there.");
        jdbc.update("insert into currency (code, name, symbol, date_created, last_updated) values (?,?,?,now(),now())",
                c, blankToNull(name), blankToNull(symbol));
        return currencies().stream().filter(x -> x.code().equals(c)).findFirst().orElseThrow();
    }

    @Transactional
    public void deleteCurrency(String code) {
        String c = normCode(code);
        if (c.equals(defaultCurrency())) throw new ResponseStatusException(BAD_REQUEST, c + " is the default currency.");
        Integer used = jdbc.queryForObject("select count(*) from payment where deleted_on is null and upper(currency) = ?", Integer.class, c);
        if (used != null && used > 0) throw new ResponseStatusException(CONFLICT, used + " expense(s) are in " + c + ".");
        jdbc.update("delete from currency where code = ?", c);
    }

    private void requireCurrency(String c) {
        Integer n = jdbc.queryForObject("select count(*) from currency where code = ?", Integer.class, c);
        if (n == null || n == 0) throw new ResponseStatusException(BAD_REQUEST, "Unknown currency " + c + " — add it first.");
    }

    private static String normCode(String code) {
        String c = code == null ? "" : code.trim().toUpperCase();
        if (!c.matches("[A-Z0-9]{2,8}")) throw new ResponseStatusException(BAD_REQUEST, "A currency code is 2–8 letters, like USD.");
        return c;
    }

    // ---------------------------------------------------------------- rates

    public record RateRow(long id, String from, String to, double rate, LocalDate validFrom, String note) {
    }

    public List<RateRow> rates() {
        return jdbc.query("select id, from_code, to_code, rate, valid_from, note from currency_rate order by from_code, to_code, valid_from desc",
                (rs, i) -> new RateRow(rs.getLong("id"), rs.getString("from_code"), rs.getString("to_code"),
                        rs.getDouble("rate"), rs.getDate("valid_from").toLocalDate(), rs.getString("note")));
    }

    @Transactional
    public RateRow addRate(String from, String to, Double rate, LocalDate validFrom, String note) {
        String f = normCode(from);
        String t = normCode(to);
        if (f.equals(t)) throw new ResponseStatusException(BAD_REQUEST, "A rate is between two different currencies.");
        requireCurrency(f);
        requireCurrency(t);
        if (rate == null || !(rate > 0)) throw new ResponseStatusException(BAD_REQUEST, "The rate must be a positive number.");
        LocalDate from0 = validFrom == null ? LocalDate.now() : validFrom;
        jdbc.update("insert into currency_rate (from_code, to_code, rate, valid_from, note, date_created) values (?,?,?,?,?,now())",
                f, t, rate, Date.valueOf(from0), blankToNull(note));
        return rates().stream().filter(r -> r.from().equals(f) && r.to().equals(t) && r.validFrom().equals(from0))
                .max(Comparator.comparingLong(RateRow::id)).orElseThrow();
    }

    @Transactional
    public void deleteRate(long id) {
        jdbc.update("delete from currency_rate where id = ?", id);
    }

    private FxConverter converter() {
        return new FxConverter(rates().stream()
                .map(r -> new FxConverter.Rate(r.from(), r.to(), r.rate(), r.validFrom())).toList());
    }

    // ---------------------------------------------------------------- categories

    public record Category(long id, String name, String code, String description, String style, Integer orderNumber,
                           int expenses) {
    }

    /** Display name: the name, else the code (most legacy categories only have a code), else #id. */
    private static String label(String name, String code, long id) {
        if (name != null && !name.isBlank()) return name.trim();
        if (code != null && !code.isBlank()) return code.trim();
        return "#" + id;
    }

    public List<Category> categories() {
        return jdbc.query("""
                select c.id, c.name, c.code, c.description, c.style, c.order_number,
                       (select count(*) from payment p where p.category_id = c.id and p.deleted_on is null) as n
                  from payment_category c where c.deleted_on is null""",
                        (rs, i) -> new Category(rs.getLong("id"), label(rs.getString("name"), rs.getString("code"), rs.getLong("id")),
                                rs.getString("code"), rs.getString("description"), rs.getString("style"),
                                (Integer) rs.getObject("order_number", Integer.class), rs.getInt("n")))
                .stream().sorted(Comparator.comparing(Category::name, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    public record CategoryInput(String name, String code, String description, String style) {
    }

    @Transactional
    public Category createCategory(CategoryInput in) {
        String name = requireName(in.name());
        jdbc.update("insert into payment_category (version, name, code, description, style, date_created, last_updated) values (0,?,?,?,?,now(),now())",
                name, blankToNull(in.code()), blankToNull(in.description()), blankToNull(in.style()));
        Long id = jdbc.queryForObject("select last_insert_id()", Long.class);
        return categories().stream().filter(c -> c.id() == id).findFirst().orElseThrow();
    }

    @Transactional
    public Category updateCategory(long id, CategoryInput in) {
        String name = requireName(in.name());
        int n = jdbc.update("update payment_category set name=?, code=?, description=?, style=?, last_updated=now() where id=? and deleted_on is null",
                name, blankToNull(in.code()), blankToNull(in.description()), blankToNull(in.style()), id);
        if (n == 0) throw new ResponseStatusException(NOT_FOUND, "No category " + id);
        return categories().stream().filter(c -> c.id() == id).findFirst().orElseThrow();
    }

    /** Remove a category nothing is filed under (soft delete, like everything else here). */
    @Transactional
    public void deleteCategory(long id) {
        Integer used = jdbc.queryForObject("select count(*) from payment where category_id = ? and deleted_on is null", Integer.class, id);
        if (used != null && used > 0) {
            throw new ResponseStatusException(CONFLICT, used + " expense(s) are in this category — move them first.");
        }
        jdbc.update("update payment_category set deleted_on = now() where id = ?", id);
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) throw new ResponseStatusException(BAD_REQUEST, "Give the category a name.");
        return name.trim();
    }

    // ---------------------------------------------------------------- expenses

    public record Expense(long id, double amount, String currency, LocalDateTime date, Long categoryId, String categoryName,
                          String summary, String description, String notes,
                          /** In the default currency at the rate of its date; null when no rate reaches it. */
                          Double converted) {
    }

    public record ExpenseInput(Double amount, String currency, LocalDateTime date, Long categoryId,
                               String summary, String description, String notes) {
    }

    private static final String EXPENSE_SELECT = """
            select p.id, p.amount, p.currency, coalesce(p.paid_on, p.date, p.date_created) as d, p.category_id,
                   c.name as cname, c.code as ccode, p.summary, p.description, p.notes
              from payment p left join payment_category c on c.id = p.category_id
             where p.deleted_on is null""";

    /** Expenses between {@code from} and {@code to} (inclusive dates, either open), newest first. */
    public List<Expense> expenses(LocalDate from, LocalDate to, Long categoryId, String q, int limit) {
        StringBuilder sql = new StringBuilder(EXPENSE_SELECT);
        List<Object> args = new ArrayList<>();
        if (from != null) {
            sql.append(" and coalesce(p.paid_on, p.date, p.date_created) >= ?");
            args.add(Timestamp.valueOf(from.atStartOfDay()));
        }
        if (to != null) {
            sql.append(" and coalesce(p.paid_on, p.date, p.date_created) < ?");
            args.add(Timestamp.valueOf(to.plusDays(1).atStartOfDay()));
        }
        if (categoryId != null) {
            sql.append(" and p.category_id = ?");
            args.add(categoryId);
        }
        if (q != null && !q.isBlank()) {
            sql.append(" and (p.summary like ? or p.description like ? or p.notes like ?)");
            String like = "%" + q.trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        sql.append(" order by d desc, p.id desc limit ").append(Math.max(1, Math.min(limit, 5000)));
        return queryExpenses(sql.toString(), args.toArray());
    }

    private List<Expense> queryExpenses(String sql, Object... args) {
        String target = defaultCurrency();
        FxConverter fx = converter();
        return jdbc.query(sql, (rs, i) -> {
            String cur = rs.getString("currency");
            String c = cur == null || cur.isBlank() ? target : cur.toUpperCase();
            LocalDateTime d = rs.getTimestamp("d").toLocalDateTime();
            double amount = rs.getDouble("amount");
            OptionalDouble conv = fx.convert(amount, c, target, d.toLocalDate());
            Long cat = (Long) rs.getObject("category_id", Long.class);
            return new Expense(rs.getLong("id"), amount, c, d, cat,
                    cat == null ? null : label(rs.getString("cname"), rs.getString("ccode"), cat),
                    rs.getString("summary"), rs.getString("description"), rs.getString("notes"),
                    conv.isPresent() ? conv.getAsDouble() : null);
        }, args);
    }

    @Transactional
    public Expense createExpense(ExpenseInput in) {
        Valid v = validate(in);
        jdbc.update("insert into payment (version, amount, currency, date, category_id, summary, description, notes, date_created, last_updated) "
                        + "values (0,?,?,?,?,?,?,?,now(),now())",
                v.amount, v.currency, Timestamp.valueOf(v.date), v.categoryId, blankToNull(in.summary()),
                blankToNull(in.description()), blankToNull(in.notes()));
        Long id = jdbc.queryForObject("select last_insert_id()", Long.class);
        return expense(id);
    }

    @Transactional
    public Expense updateExpense(long id, ExpenseInput in) {
        Valid v = validate(in);
        // paid_on is cleared: the one date edited here is the expense's date, and a stale paid_on
        // would otherwise keep winning in coalesce(paid_on, date).
        int n = jdbc.update("update payment set amount=?, currency=?, date=?, paid_on=null, category_id=?, summary=?, description=?, notes=?, "
                        + "last_updated=now(), version=version+1 where id=? and deleted_on is null",
                v.amount, v.currency, Timestamp.valueOf(v.date), v.categoryId, blankToNull(in.summary()),
                blankToNull(in.description()), blankToNull(in.notes()), id);
        if (n == 0) throw new ResponseStatusException(NOT_FOUND, "No expense " + id);
        return expense(id);
    }

    @Transactional
    public void deleteExpense(long id) {
        jdbc.update("update payment set deleted_on = now() where id = ?", id);
    }

    private Expense expense(long id) {
        return queryExpenses(EXPENSE_SELECT + " and p.id = ?", id).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "No expense " + id));
    }

    private record Valid(double amount, String currency, LocalDateTime date, Long categoryId) {
    }

    private Valid validate(ExpenseInput in) {
        if (in.amount() == null || !(in.amount() > 0)) throw new ResponseStatusException(BAD_REQUEST, "The amount must be a positive number.");
        String cur = in.currency() == null || in.currency().isBlank() ? defaultCurrency() : normCode(in.currency());
        requireCurrency(cur);
        if (in.categoryId() != null) {
            Integer n = jdbc.queryForObject("select count(*) from payment_category where id = ? and deleted_on is null", Integer.class, in.categoryId());
            if (n == null || n == 0) throw new ResponseStatusException(BAD_REQUEST, "No such category.");
        }
        return new Valid(in.amount(), cur, in.date() == null ? LocalDateTime.now() : in.date(), in.categoryId());
    }

    // ---------------------------------------------------------------- reports

    /** One category's spending: per month (12 values) in a year report, or per year in the overview. */
    public record CategorySeries(Long id, String name, double[] values, double total) {
    }

    /** Expenses that couldn't be converted into the report's currency, by currency. */
    public record Unconverted(String currency, int count, double amount) {
    }

    public record YearReport(int year, String currency, double[] monthTotals, double total,
                             List<CategorySeries> categories, List<Unconverted> unconverted) {
    }

    public record YearsReport(String currency, int[] years, double[] yearTotals, List<CategorySeries> categories,
                              List<Unconverted> unconverted) {
    }

    private record Row(long id, double amount, String currency, LocalDate date, Long categoryId, String categoryName) {
    }

    private List<Row> rows(LocalDate from, LocalDate toExclusive) {
        String def = defaultCurrency();
        List<Object> args = new ArrayList<>();
        String where = "";
        if (from != null) {
            where = " and coalesce(p.paid_on, p.date, p.date_created) >= ? and coalesce(p.paid_on, p.date, p.date_created) < ?";
            args.add(Timestamp.valueOf(from.atStartOfDay()));
            args.add(Timestamp.valueOf(toExclusive.atStartOfDay()));
        }
        return jdbc.query(EXPENSE_SELECT + where, (rs, i) -> {
            String cur = rs.getString("currency");
            Long cat = (Long) rs.getObject("category_id", Long.class);
            return new Row(rs.getLong("id"), rs.getDouble("amount"), cur == null || cur.isBlank() ? def : cur.toUpperCase(),
                    rs.getTimestamp("d").toLocalDateTime().toLocalDate(), cat,
                    cat == null ? "Uncategorised" : label(rs.getString("cname"), rs.getString("ccode"), cat));
        }, args.toArray());
    }

    /** Month by month, per category, in {@code currency} (default currency when null). */
    public YearReport yearReport(int year, String currency) {
        String target = currency == null || currency.isBlank() ? defaultCurrency() : normCode(currency);
        FxConverter fx = converter();
        double[] months = new double[12];
        Map<Long, double[]> perCat = new LinkedHashMap<>();
        Map<Long, String> names = new LinkedHashMap<>();
        Map<String, Unconverted> missing = new LinkedHashMap<>();
        for (Row r : rows(LocalDate.of(year, 1, 1), LocalDate.of(year + 1, 1, 1))) {
            OptionalDouble v = fx.convert(r.amount(), r.currency(), target, r.date());
            if (v.isEmpty()) {
                missing.merge(r.currency(), new Unconverted(r.currency(), 1, r.amount()),
                        (a, b) -> new Unconverted(a.currency(), a.count() + 1, a.amount() + b.amount()));
                continue;
            }
            int m = r.date().getMonthValue() - 1;
            months[m] += v.getAsDouble();
            long key = r.categoryId() == null ? -1 : r.categoryId();
            perCat.computeIfAbsent(key, k -> new double[12])[m] += v.getAsDouble();
            names.put(key, r.categoryName());
        }
        List<CategorySeries> cats = perCat.entrySet().stream()
                .map(e -> new CategorySeries(e.getKey() < 0 ? null : e.getKey(), names.get(e.getKey()), e.getValue(), sum(e.getValue())))
                .sorted(Comparator.comparingDouble(CategorySeries::total).reversed()).toList();
        return new YearReport(year, target, months, sum(months), cats, new ArrayList<>(missing.values()));
    }

    /** Year by year, per category, across every year with expenses. */
    public YearsReport yearsReport(String currency) {
        String target = currency == null || currency.isBlank() ? defaultCurrency() : normCode(currency);
        FxConverter fx = converter();
        List<Row> all = rows(null, null);
        int first = all.stream().mapToInt(r -> r.date().getYear()).min().orElse(LocalDate.now().getYear());
        int last = Math.max(all.stream().mapToInt(r -> r.date().getYear()).max().orElse(first), first);
        int n = last - first + 1;
        int[] years = new int[n];
        for (int i = 0; i < n; i++) years[i] = first + i;
        double[] totals = new double[n];
        Map<Long, double[]> perCat = new LinkedHashMap<>();
        Map<Long, String> names = new LinkedHashMap<>();
        Map<String, Unconverted> missing = new LinkedHashMap<>();
        for (Row r : all) {
            OptionalDouble v = fx.convert(r.amount(), r.currency(), target, r.date());
            if (v.isEmpty()) {
                missing.merge(r.currency(), new Unconverted(r.currency(), 1, r.amount()),
                        (a, b) -> new Unconverted(a.currency(), a.count() + 1, a.amount() + b.amount()));
                continue;
            }
            int y = r.date().getYear() - first;
            totals[y] += v.getAsDouble();
            long key = r.categoryId() == null ? -1 : r.categoryId();
            perCat.computeIfAbsent(key, k -> new double[n])[y] += v.getAsDouble();
            names.put(key, r.categoryName());
        }
        List<CategorySeries> cats = perCat.entrySet().stream()
                .map(e -> new CategorySeries(e.getKey() < 0 ? null : e.getKey(), names.get(e.getKey()), e.getValue(), sum(e.getValue())))
                .sorted(Comparator.comparingDouble(CategorySeries::total).reversed()).toList();
        return new YearsReport(target, years, totals, cats, new ArrayList<>(missing.values()));
    }

    private static double sum(double[] v) {
        double s = 0;
        for (double x : v) s += x;
        return s;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
