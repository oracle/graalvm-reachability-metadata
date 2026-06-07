/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.opencsv.bean.ConverterNumber;
import com.opencsv.bean.CsvBindByName;
import com.opencsv.bean.CsvNumber;
import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.bean.HeaderColumnNameMappingStrategy;
import com.opencsv.exceptions.CsvBadConverterException;
import com.opencsv.exceptions.CsvDataTypeMismatchException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

public class ConverterNumberTest {
    @Test
    void parsesLocalizedNumberFieldUsingAnnotatedBean() {
        List<LocalizedNumberBean> beans = new CsvToBeanBuilder<LocalizedNumberBean>(
                new StringReader("amount\n\"1,234.50\"\n"))
                .withType(LocalizedNumberBean.class)
                .build()
                .parse();

        assertThat(beans).hasSize(1);
        assertThat(beans.get(0).amount).isEqualByComparingTo("1234.50");
    }

    @Test
    void reportsCsvNumberOnNonNumberField() {
        HeaderColumnNameMappingStrategy<NonNumberFieldBean> strategy = new HeaderColumnNameMappingStrategy<>();

        assertThatExceptionOfType(CsvBadConverterException.class)
                .isThrownBy(() -> strategy.setType(NonNumberFieldBean.class));
    }

    @Test
    void reportsInvalidReadNumberFormat() {
        HeaderColumnNameMappingStrategy<InvalidReadFormatBean> strategy = new HeaderColumnNameMappingStrategy<>();

        assertThatExceptionOfType(CsvBadConverterException.class)
                .isThrownBy(() -> strategy.setType(InvalidReadFormatBean.class));
    }

    @Test
    void reportsInvalidNumberValue() {
        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> new CsvToBeanBuilder<LocalizedNumberBean>(new StringReader("amount\nnot-a-number\n"))
                        .withType(LocalizedNumberBean.class)
                        .build()
                        .parse())
                .withCauseInstanceOf(CsvDataTypeMismatchException.class);
    }

    @Test
    void reportsInvalidNumberValueThroughConverter() {
        ConverterNumber converter = new ConverterNumber(
                Integer.class, Locale.US.toLanguageTag(), Locale.US.toLanguageTag(), Locale.ENGLISH, "#,##0", "#,##0");

        assertThatExceptionOfType(CsvDataTypeMismatchException.class)
                .isThrownBy(() -> converter.convertToRead("not-a-number"));
    }

    public static class LocalizedNumberBean {
        @CsvBindByName(column = "amount", locale = "en-US")
        @CsvNumber("#,##0.00")
        public BigDecimal amount;
    }

    public static class NonNumberFieldBean {
        @CsvBindByName(column = "amount")
        @CsvNumber("#,##0.00")
        public String amount;
    }

    public static class InvalidReadFormatBean {
        @CsvBindByName(column = "amount")
        @CsvNumber("#,##0.00.00")
        public BigDecimal amount;
    }
}
