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

        expenses.createExpense(new ExpenseInput(10.0, base, LocalDateTime.of(year, 1, 5, 10, 0), food.id(), "bread", null, null));
        expenses.createExpense(new ExpenseInput(500.0, local, LocalDateTime.of(year, 1, 20, 10, 0), food.id(), "fruit", null, null)); // 5 ZZB
        expenses.createExpense(new ExpenseInput(2000.0, local, LocalDateTime.of(year, 7, 1, 10, 0), car.id(), "fuel", null, null));  // 2 ZZB
        expenses.createExpense(new ExpenseInput(7.0, norate, LocalDateTime.of(year, 7, 2, 10, 0), car.id(), "no rate", null, null));

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
        expenses.updateExpense(e.id(), new ExpenseInput(10.0, base, LocalDateTime.of(year, 2, 1, 9, 0), car.id(), "bread", null, null));
        assertThat(expenses.yearReport(year, base).monthTotals()[1]).isCloseTo(10.0, within(1e-9));
    }

    @Test
    void validation() {
        assertThatThrownBy(() -> expenses.createExpense(new ExpenseInput(-1.0, null, null, null, null, null, null)))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> expenses.createExpense(new ExpenseInput(1.0, "QQQQ", null, null, null, null, null)))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> expenses.addRate("USD", "USD", 1.0, null, null)).isInstanceOf(ResponseStatusException.class);
    }
}
