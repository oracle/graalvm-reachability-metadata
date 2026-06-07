/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.opencsv.bean.CsvBindByName;
import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.exceptions.CsvMalformedLineException;
import java.io.CharConversionException;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Locale;
import org.junit.jupiter.api.Test;

public class LineExecutorTest {
    @Test
    void parseReportsMalformedCsvFromBackgroundReader() {
        CsvToBean<NamedBean> csvToBean = new CsvToBeanBuilder<NamedBean>(
                new StringReader("name\n\"unterminated"))
                .withType(NamedBean.class)
                .withErrorLocale(Locale.ROOT)
                .build();

        RuntimeException exception = assertThrows(RuntimeException.class, csvToBean::parse);

        assertInstanceOf(CsvMalformedLineException.class, exception.getCause());
    }

    @Test
    void parseReportsIoFailureFromBackgroundReader() {
        CsvToBean<NamedBean> csvToBean = new CsvToBeanBuilder<NamedBean>(
                new HeaderThenFailingReader())
                .withType(NamedBean.class)
                .withErrorLocale(Locale.ROOT)
                .build();

        RuntimeException exception = assertThrows(RuntimeException.class, csvToBean::parse);

        assertInstanceOf(CharConversionException.class, exception.getCause());
    }

    public static class NamedBean {
        @CsvBindByName(column = "name")
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    private static final class HeaderThenFailingReader extends Reader {
        private boolean headerRead;

        @Override
        public int read(char[] buffer, int offset, int length) throws IOException {
            if (!headerRead) {
                char[] header = "name\n".toCharArray();
                System.arraycopy(header, 0, buffer, offset, header.length);
                headerRead = true;
                return header.length;
            }
            throw new CharConversionException("data unavailable");
        }

        @Override
        public void close() {
        }
    }
}
