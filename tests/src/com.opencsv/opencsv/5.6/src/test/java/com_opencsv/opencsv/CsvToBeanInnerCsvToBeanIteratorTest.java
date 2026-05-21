/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import com.opencsv.bean.CsvBindByName;
import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.Iterator;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class CsvToBeanInnerCsvToBeanIteratorTest {
    @Test
    void reportsMalformedCsvWhenIteratorReadsSingleLine() {
        CsvToBean<IteratorBean> csvToBean = csvToBean("name\n\"unterminated");

        assertThatThrownBy(csvToBean::iterator)
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Error parsing CSV")
                .hasCauseInstanceOf(IOException.class);
    }

    @Test
    void rejectsRemovingFromIteratorWithLocalizedMessage() {
        CsvToBean<IteratorBean> csvToBean = csvToBean("name\nAlice\n");
        Iterator<IteratorBean> iterator = csvToBean.iterator();

        assertThat(iterator).hasNext();
        assertThatThrownBy(iterator::remove)
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("read-only iterator");
    }

    private static CsvToBean<IteratorBean> csvToBean(String csv) {
        return new CsvToBeanBuilder<IteratorBean>(new StringReader(csv))
                .withType(IteratorBean.class)
                .withErrorLocale(Locale.US)
                .build();
    }

    public static class IteratorBean {
        @CsvBindByName(column = "name")
        public String name;
    }
}
