/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import com.opencsv.CSVReader;
import com.opencsv.bean.CsvBindByName;
import com.opencsv.bean.CsvToBeanBuilder;
import org.junit.jupiter.api.Test;

import java.io.Reader;
import java.io.StringReader;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class CsvToBeanBuilderTest {

    @Test
    void readerConstructorRequiresReader() {
        assertThatThrownBy(() -> new CsvToBeanBuilder<NamedBean>((Reader) null))
                .isInstanceOf(IllegalArgumentException.class)
                .satisfies(exception -> assertThat(exception.getMessage()).isNotBlank());
    }

    @Test
    void csvReaderConstructorRequiresCsvReader() {
        assertThatThrownBy(() -> new CsvToBeanBuilder<NamedBean>((CSVReader) null))
                .isInstanceOf(IllegalArgumentException.class)
                .satisfies(exception -> assertThat(exception.getMessage()).isNotBlank());
    }

    @Test
    void buildRequiresTypeOrMappingStrategy() {
        CsvToBeanBuilder<NamedBean> builder = new CsvToBeanBuilder<NamedBean>(new StringReader("name\nAlice\n"))
                .withErrorLocale(Locale.US);

        assertThatThrownBy(builder::build)
                .isInstanceOf(IllegalStateException.class)
                .satisfies(exception -> assertThat(exception.getMessage()).isNotBlank());
    }

    @Test
    void ignoreFieldRequiresFieldAssignableToConfiguredType() throws Exception {
        CsvToBeanBuilder<NamedBean> builder = new CsvToBeanBuilder<NamedBean>(new StringReader("name\nAlice\n"))
                .withErrorLocale(Locale.US);

        assertThatThrownBy(() -> builder.withIgnoreField(NamedBean.class, OtherBean.class.getField("other")))
                .isInstanceOf(IllegalArgumentException.class)
                .satisfies(exception -> assertThat(exception.getMessage()).isNotBlank());
    }

    public static class NamedBean {
        @CsvBindByName
        public String name;
    }

    public static class OtherBean {
        public String other;
    }
}
