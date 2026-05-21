/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import com.opencsv.CSVReader;
import com.opencsv.bean.CsvToBeanBuilder;
import org.junit.jupiter.api.Test;

import java.io.Reader;
import java.lang.reflect.Field;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class CsvToBeanBuilderTest {
    @Test
    void rejectsNullReader() {
        assertThatThrownBy(() -> new CsvToBeanBuilder<Object>((Reader) null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullCsvReader() {
        assertThatThrownBy(() -> new CsvToBeanBuilder<Object>((CSVReader) null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reportsMissingTypeOrMappingStrategyWithConfiguredLocale() {
        assertThatThrownBy(() -> new CsvToBeanBuilder<Object>(Reader.nullReader())
                .withErrorLocale(Locale.US)
                .build())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsInconsistentIgnoredFieldWithConfiguredLocale() throws NoSuchFieldException {
        Field otherField = OtherBean.class.getDeclaredField("other");

        assertThatThrownBy(() -> new CsvToBeanBuilder<SimpleBean>(Reader.nullReader())
                .withErrorLocale(Locale.US)
                .withIgnoreField(SimpleBean.class, otherField))
                .isInstanceOf(IllegalArgumentException.class);
    }

    public static class SimpleBean {
        public String name;
    }

    public static class OtherBean {
        public String other;
    }
}
