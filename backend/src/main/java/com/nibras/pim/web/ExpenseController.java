package com.nibras.pim.web;

import com.nibras.pim.service.expenses.ExpenseService;
import com.nibras.pim.service.expenses.ExpenseService.Category;
import com.nibras.pim.service.expenses.ExpenseService.CategoryInput;
import com.nibras.pim.service.expenses.ExpenseService.Currency;
import com.nibras.pim.service.expenses.ExpenseService.Expense;
import com.nibras.pim.service.expenses.ExpenseService.ExpenseInput;
import com.nibras.pim.service.expenses.ExpenseService.RateRow;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Expenses, their categories, currencies and exchange rates, and the spending reports. */
@RestController
@RequestMapping("/api/expenses")
public class ExpenseController {

    private final ExpenseService service;

    public ExpenseController(ExpenseService service) {
        this.service = service;
    }

    @GetMapping
    public List<Expense> list(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                              @RequestParam(required = false) Long categoryId,
                              @RequestParam(required = false) String q,
                              @RequestParam(defaultValue = "1000") int limit,
                              @RequestParam(defaultValue = "ALL") ExpenseService.Status status) {
        return service.expenses(from, to, categoryId, q, limit, status, false);
    }

    /** Lessons learned: every expense with a lesson written down, newest first. */
    @GetMapping("/lessons")
    public List<Expense> lessons(@RequestParam(required = false) Long categoryId,
                                 @RequestParam(required = false) String q) {
        return service.expenses(null, null, categoryId, q, 5000, ExpenseService.Status.ALL, true);
    }

    /** A to-be-paid expense paid today. */
    @PostMapping("/{id}/paid")
    public Expense markPaid(@PathVariable long id) {
        return service.markPaid(id);
    }

    @PostMapping
    public Expense create(@RequestBody ExpenseInput in) {
        return service.createExpense(in);
    }

    @PutMapping("/{id}")
    public Expense update(@PathVariable long id, @RequestBody ExpenseInput in) {
        return service.updateExpense(id, in);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable long id) {
        service.deleteExpense(id);
    }

    @GetMapping("/categories")
    public List<Category> categories() {
        return service.categories();
    }

    @PostMapping("/categories")
    public Category createCategory(@RequestBody CategoryInput in) {
        return service.createCategory(in);
    }

    @PutMapping("/categories/{id}")
    public Category updateCategory(@PathVariable long id, @RequestBody CategoryInput in) {
        return service.updateCategory(id, in);
    }

    @DeleteMapping("/categories/{id}")
    public void deleteCategory(@PathVariable long id) {
        service.deleteCategory(id);
    }

    @GetMapping("/currencies")
    public Map<String, Object> currencies() {
        return Map.of("defaultCurrency", service.defaultCurrency(), "currencies", service.currencies());
    }

    public record CurrencyInput(String code, String name, String symbol) {
    }

    @PostMapping("/currencies")
    public Currency addCurrency(@RequestBody CurrencyInput in) {
        return service.addCurrency(in.code(), in.name(), in.symbol());
    }

    @DeleteMapping("/currencies/{code}")
    public void deleteCurrency(@PathVariable String code) {
        service.deleteCurrency(code);
    }

    public record DefaultCurrency(String code) {
    }

    @PutMapping("/currencies/default")
    public void setDefault(@RequestBody DefaultCurrency body) {
        service.setDefaultCurrency(body.code());
    }

    @GetMapping("/rates")
    public List<RateRow> rates() {
        return service.rates();
    }

    public record RateInput(String from, String to, Double rate,
                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate validFrom, String note) {
    }

    @PostMapping("/rates")
    public RateRow addRate(@RequestBody RateInput in) {
        return service.addRate(in.from(), in.to(), in.rate(), in.validFrom(), in.note());
    }

    @DeleteMapping("/rates/{id}")
    public void deleteRate(@PathVariable long id) {
        service.deleteRate(id);
    }

    @GetMapping("/report/year")
    public ExpenseService.YearReport year(@RequestParam int year, @RequestParam(required = false) String currency) {
        return service.yearReport(year, currency);
    }

    @GetMapping("/report/years")
    public ExpenseService.YearsReport years(@RequestParam(required = false) String currency) {
        return service.yearsReport(currency);
    }
}
