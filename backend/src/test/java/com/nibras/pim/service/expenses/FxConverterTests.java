package com.nibras.pim.service.expenses;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class FxConverterTests {

    private static final LocalDate D2019 = LocalDate.of(2019, 6, 1);
    private static final LocalDate D2024 = LocalDate.of(2024, 6, 1);

    private final FxConverter fx = new FxConverter(List.of(
            new FxConverter.Rate("USD", "LBP", 1500, LocalDate.of(2000, 1, 1)),
            new FxConverter.Rate("USD", "LBP", 89500, LocalDate.of(2024, 1, 1)),
            new FxConverter.Rate("EUR", "USD", 1.1, LocalDate.of(2010, 1, 1))));

    @Test
    void theRateInForceOnTheDateIsUsed() {
        assertThat(fx.convert(3000, "LBP", "USD", D2019).getAsDouble()).isCloseTo(2.0, within(1e-9));
        assertThat(fx.convert(179000, "LBP", "USD", D2024).getAsDouble()).isCloseTo(2.0, within(1e-9));
    }

    @Test
    void aRateWorksBothWaysAndThroughAThirdCurrency() {
        assertThat(fx.convert(2, "USD", "LBP", D2019).getAsDouble()).isCloseTo(3000, within(1e-6));
        assertThat(fx.convert(10, "EUR", "LBP", D2019).getAsDouble()).isCloseTo(10 * 1.1 * 1500, within(1e-6));
    }

    @Test
    void beforeTheFirstRateTheEarliestIsUsed_andNoPathIsEmpty() {
        assertThat(fx.convert(1, "EUR", "USD", LocalDate.of(2005, 1, 1)).getAsDouble()).isCloseTo(1.1, within(1e-9));
        assertThat(fx.convert(1, "GBP", "USD", D2019)).isEmpty();
        assertThat(fx.convert(5, "usd", "USD", D2019).getAsDouble()).isEqualTo(5);
    }
}
