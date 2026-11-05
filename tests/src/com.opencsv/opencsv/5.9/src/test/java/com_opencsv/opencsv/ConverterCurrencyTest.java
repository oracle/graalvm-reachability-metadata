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
import org.junit.jupiter.api.Timeout;

import java.util.Currency;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Timeout(120)
public class ConverterCurrencyTest {
    @Test
    void convertsCurrencyCodesAndReportsUnknownCodes() throws Exception {
        ConverterCurrency converter = new ConverterCurrency(Locale.US);
        assertThat(converter.convertToRead("EUR")).isEqualTo(Currency.getInstance("EUR"));
        assertThat(converter.convertToWrite(Currency.getInstance("USD"))).isEqualTo("USD");
        assertThatThrownBy(() -> converter.convertToRead("invalid-currency"))
                .isInstanceOf(CsvDataTypeMismatchException.class)
                .hasMessageContaining("invalid-currency")
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }
}
