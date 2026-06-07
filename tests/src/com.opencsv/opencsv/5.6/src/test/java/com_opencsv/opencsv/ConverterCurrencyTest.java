/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.opencsv.bean.ConverterCurrency;
import com.opencsv.exceptions.CsvDataTypeMismatchException;
import java.util.Currency;
import java.util.Locale;
import org.junit.jupiter.api.Test;

public class ConverterCurrencyTest {
    @Test
    void parsesIsoCurrencyCode() throws Exception {
        ConverterCurrency converter = new ConverterCurrency(Locale.ENGLISH);

        Object value = converter.convertToRead("USD");

        assertThat(value).isEqualTo(Currency.getInstance("USD"));
    }

    @Test
    void reportsInvalidCurrencyCode() {
        ConverterCurrency converter = new ConverterCurrency(Locale.ENGLISH);

        assertThatExceptionOfType(CsvDataTypeMismatchException.class)
                .isThrownBy(() -> converter.convertToRead("not-a-currency"))
                .withMessageContaining("not-a-currency")
                .withCauseInstanceOf(IllegalArgumentException.class);
    }
}
