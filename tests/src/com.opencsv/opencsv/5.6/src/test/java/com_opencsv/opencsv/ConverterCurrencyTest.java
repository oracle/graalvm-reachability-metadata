/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import com.opencsv.bean.ConverterCurrency;
import com.opencsv.exceptions.CsvDataTypeMismatchException;
import org.junit.jupiter.api.Test;

import java.util.Currency;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ConverterCurrencyTest {
    @Test
    void convertsIsoCurrencyCode() throws CsvDataTypeMismatchException {
        ConverterCurrency converter = new ConverterCurrency(Locale.US);

        Object converted = converter.convertToRead("USD");

        assertThat(converted).isEqualTo(Currency.getInstance("USD"));
    }

    @Test
    void loadsErrorBundleWhenCurrencyCodeIsInvalid() {
        ConverterCurrency converter = new ConverterCurrency(Locale.US);

        assertThatThrownBy(() -> converter.convertToRead("not-a-currency"))
                .isInstanceOf(CsvDataTypeMismatchException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not-a-currency")
                .hasMessageContaining("valid ISO 4217 currency code");
    }

    @Test
    void writesCurrencyAsIsoCode() throws CsvDataTypeMismatchException {
        ConverterCurrency converter = new ConverterCurrency(Locale.US);

        String converted = converter.convertToWrite(Currency.getInstance("EUR"));

        assertThat(converted).isEqualTo("EUR");
    }
}
