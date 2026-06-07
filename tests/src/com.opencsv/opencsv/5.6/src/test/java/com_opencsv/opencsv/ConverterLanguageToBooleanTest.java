/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.opencsv.bean.CsvCustomBindByName;
import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.bean.StatefulBeanToCsv;
import com.opencsv.bean.StatefulBeanToCsvBuilder;
import com.opencsv.bean.customconverter.ConvertGermanToBoolean;
import com.opencsv.exceptions.CsvDataTypeMismatchException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Locale;
import org.junit.jupiter.api.Test;

public class ConverterLanguageToBooleanTest {
    @Test
    void reportsInvalidLocalizedBooleanWhenReading() {
        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> new CsvToBeanBuilder<GermanBooleanBean>(
                        new StringReader("active\nmaybe\n"))
                        .withType(GermanBooleanBean.class)
                        .withErrorLocale(Locale.ENGLISH)
                        .build()
                        .parse())
                .withCauseInstanceOf(CsvDataTypeMismatchException.class)
                .satisfies(exception -> assertThat(exception.getCause())
                        .hasMessageContaining("not a boolean value"));
    }

    @Test
    void reportsNonBooleanFieldWhenWritingLocalizedBoolean() throws Exception {
        StringWriter writer = new StringWriter();
        StatefulBeanToCsv<NonBooleanFieldBean> beanToCsv =
                new StatefulBeanToCsvBuilder<NonBooleanFieldBean>(writer)
                        .withErrorLocale(Locale.ENGLISH)
                        .build();

        assertThatExceptionOfType(CsvDataTypeMismatchException.class)
                .isThrownBy(() -> beanToCsv.write(new NonBooleanFieldBean("not-a-boolean")))
                .withMessageContaining("Boolean or boolean");
    }

    public static class GermanBooleanBean {
        @CsvCustomBindByName(column = "active", converter = ConvertGermanToBoolean.class)
        public Boolean active;
    }

    public static class NonBooleanFieldBean {
        @CsvCustomBindByName(column = "active", converter = ConvertGermanToBoolean.class)
        public String active;

        public NonBooleanFieldBean() {
        }

        public NonBooleanFieldBean(String active) {
            this.active = active;
        }
    }
}
