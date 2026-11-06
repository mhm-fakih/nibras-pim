package com.nibras.pim;

import com.nibras.pim.service.expenses.ExpenseService;
import com.nibras.pim.service.expenses.ExpenseService.CategoryInput;
import com.nibras.pim.service.expenses.ExpenseService.ExpenseInput;
import com.nibras.pim.support.AuthTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/** Expenses in several currencies, summed per category and month in one currency. */
class ExpensesTests extends AuthTestSupport {

    @Autowired
    ExpenseService expenses;

    @Test
    void monthlyTotalsConvertEachExpenseAtItsDatesRate() {
        // The test database outlives a run: a year and currency codes of this run's own, so earlier
        // runs' expenses and rates can't leak into these totals.
        long n = System.nanoTime();
        int year = 3000 + (int) (n % 5000);
        String tag = "x" + n % 100000;
        String base = ("B" + Long.toString(n, 36)).toUpperCase().substring(0, 7);
        String local = ("L" + Long.toString(n, 36)).toUpperCase().substring(0, 7);
        String norate = ("N" + Long.toString(n, 36)).toUpperCase().substring(0, 7);
        var food = expenses.createCategory(new CategoryInput("Food " + tag, null, null, null));
        var car = expenses.createCategory(new CategoryInput("Car " + tag, null, null, null));
        expenses.addCurrency(base, "Test base", null);
        expenses.addCurrency(local, "Test local", null);
        expenses.addCurrency(norate, "No rate", null);
        expenses.addRate(base, local, 100.0, LocalDate.of(year, 1, 1), null);
        expenses.addRate(base, local, 1000.0, LocalDate.of(year, 6, 1), null);

        expenses.createExpense(new ExpenseInput(10.0, base, LocalDateTime.of(year, 1, 5, 10, 0), food.id(), "bread", null, null, true, null));
        expenses.createExpense(new ExpenseInput(500.0, local, LocalDateTime.of(year, 1, 20, 10, 0), food.id(), "fruit", null, null, true, null)); // 5 ZZB
        expenses.createExpense(new ExpenseInput(2000.0, local, LocalDateTime.of(year, 7, 1, 10, 0), car.id(), "fuel", null, null, true, null));  // 2 ZZB
        expenses.createExpense(new ExpenseInput(7.0, norate, LocalDateTime.of(year, 7, 2, 10, 0), car.id(), "no rate", null, null, true, null));

        var r = expenses.yearReport(year, base);
        assertThat(r.currency()).isEqualTo(base);
        assertThat(r.monthTotals()[0]).isCloseTo(15.0, within(1e-9));
        assertThat(r.monthTotals()[6]).isCloseTo(2.0, within(1e-9));
        assertThat(r.total()).isCloseTo(17.0, within(1e-9));
        assertThat(r.categories()).extracting(ExpenseService.CategorySeries::name).containsExactly("Food " + tag, "Car " + tag);
        assertThat(r.unconverted()).singleElement().satisfies(u -> {
            assertThat(u.currency()).isEqualTo(norate);
            assertThat(u.count()).isEqualTo(1);
        });

        // The same year in the local currency.
        assertThat(expenses.yearReport(year, local).total()).isCloseTo(10 * 100 + 500 + 2000, within(1e-6));
        assertThat(expenses.yearsReport(base).years()).contains(year);

        // A category with expenses can't be removed; an empty one can.
        assertThatThrownBy(() -> expenses.deleteCategory(food.id())).isInstanceOf(ResponseStatusException.class);
        var empty = expenses.createCategory(new CategoryInput("Empty " + tag, null, null, null));
        expenses.deleteCategory(empty.id());
        assertThat(expenses.categories()).noneMatch(c -> c.id() == empty.id());

        // Editing an expense moves it.
        var e = expenses.expenses(LocalDate.of(year, 1, 1), LocalDate.of(year, 1, 31), food.id(), "bread", 10).get(0);
        expenses.updateExpense(e.id(), new ExpenseInput(10.0, base, LocalDateTime.of(year, 2, 1, 9, 0), car.id(), "bread", null, null, true, null));
        assertThat(expenses.yearReport(year, base).monthTotals()[1]).isCloseTo(10.0, within(1e-9));
    }

    @Test
    void budgetedPaymentsCountApartUntilPaidAndLessonsAreListed() {
        long n = System.nanoTime();
        String tag = "b" + n % 100000;
        String cur = ("C" + Long.toString(n, 36)).toUpperCase().substring(0, 7);
        expenses.addCurrency(cur, "Budget test", null);
        var cat = expenses.createCategory(new CategoryInput("Budget " + tag, null, null, null));
        // Paid not said: a date ahead means "to be paid", today means paid.
        LocalDateTime ahead = LocalDateTime.now().plusYears(1).withMonth(3).withDayOfMonth(10);
        var planned = expenses.createExpense(new ExpenseInput(40.0, cur, ahead, cat.id(), "rent " + tag, null, null, null, null));
        assertThat(planned.paid()).isFalse();
        var today = expenses.createExpense(new ExpenseInput(5.0, cur, LocalDateTime.now(), cat.id(), "tea " + tag, null,
                "a note", null, "Buy in bulk " + tag));
        assertThat(today.paid()).isTrue();
        assertThat(today.lesson()).isEqualTo("Buy in bulk " + tag);

        var r = expenses.yearReport(ahead.getYear(), cur);
        assertThat(r.total()).isCloseTo(0.0, within(1e-9));
        assertThat(r.monthBudgeted()[2]).isCloseTo(40.0, within(1e-9));
        assertThat(r.budgetedTotal()).isCloseTo(40.0, within(1e-9));
        assertThat(expenses.expenses(null, null, cat.id(), null, 10, ExpenseService.Status.PLANNED, false))
                .extracting(ExpenseService.Expense::id).containsExactly(planned.id());

        // Paid today: it leaves the budget and counts as spent, this month.
        var paid = expenses.markPaid(planned.id());
        assertThat(paid.paid()).isTrue();
        assertThat(paid.date().toLocalDate()).isEqualTo(LocalDate.now());
        assertThat(expenses.yearReport(ahead.getYear(), cur).budgetedTotal()).isCloseTo(0.0, within(1e-9));
        assertThat(expenses.yearReport(LocalDate.now().getYear(), cur).total()).isCloseTo(45.0, within(1e-9));

        // Lessons: only expenses with one, and searchable by its text.
        assertThat(expenses.expenses(null, null, cat.id(), null, 10, ExpenseService.Status.ALL, true))
                .extracting(ExpenseService.Expense::id).containsExactly(today.id());
        assertThat(expenses.expenses(null, null, null, "bulk " + tag, 10, ExpenseService.Status.ALL, true)).hasSize(1);

        // Set back to "to be paid" by an edit: paid_on is cleared.
        var back = expenses.updateExpense(today.id(), new ExpenseInput(5.0, cur, LocalDateTime.now(), cat.id(), "tea " + tag,
                null, "a note", false, "Buy in bulk " + tag));
        assertThat(back.paid()).isFalse();
    }

    @Test
    void validation() {
        assertThatThrownBy(() -> expenses.createExpense(new ExpenseInput(-1.0, null, null, null, null, null, null, true, null)))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> expenses.createExpense(new ExpenseInput(1.0, "QQQQ", null, null, null, null, null, true, null)))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> expenses.addRate("USD", "USD", 1.0, null, null)).isInstanceOf(ResponseStatusException.class);
    }
}
