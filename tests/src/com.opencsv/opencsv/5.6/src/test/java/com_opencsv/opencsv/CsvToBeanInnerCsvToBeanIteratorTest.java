/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.opencsv.CSVReader;
import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.MappingStrategy;
import com.opencsv.exceptions.CsvBeanIntrospectionException;
import com.opencsv.exceptions.CsvChainedException;
import com.opencsv.exceptions.CsvFieldAssignmentException;
import com.opencsv.exceptions.CsvRequiredFieldEmptyException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Iterator;
import java.util.Locale;
import org.junit.jupiter.api.Test;

public class CsvToBeanInnerCsvToBeanIteratorTest {
    @Test
    void iteratorWrapsReadFailure() throws IOException {
        CsvToBean<Object> csvToBean = csvToBeanWith(new FailingReader());

        RuntimeException exception = assertThrows(RuntimeException.class, csvToBean::iterator);

        assertTrue(exception.getCause() instanceof FileNotFoundException);
        assertEquals("csv source unavailable", exception.getCause().getMessage());
    }

    @Test
    void iteratorRemoveIsUnsupported() throws IOException {
        CsvToBean<Object> csvToBean = csvToBeanWith(new StringReader(""));

        Iterator<Object> iterator = csvToBean.iterator();

        assertThrows(UnsupportedOperationException.class, iterator::remove);
    }

    private static CsvToBean<Object> csvToBeanWith(Reader source) throws IOException {
        CSVReader csvReader = new CSVReader(source);
        csvReader.setErrorLocale(Locale.ROOT);

        CsvToBean<Object> csvToBean = new CsvToBean<>();
        csvToBean.setCsvReader(csvReader);
        csvToBean.setMappingStrategy(new NoOpMappingStrategy());
        csvToBean.setErrorLocale(Locale.ROOT);
        return csvToBean;
    }

    private static final class FailingReader extends Reader {
        @Override
        public int read(char[] chars, int offset, int length) throws IOException {
            throw new FileNotFoundException("csv source unavailable");
        }

        @Override
        public void close() {
        }
    }

    private static final class NoOpMappingStrategy implements MappingStrategy<Object> {
        @Override
        public void captureHeader(CSVReader reader) {
        }

        @Override
        public String[] generateHeader(Object bean) throws CsvRequiredFieldEmptyException {
            return new String[0];
        }

        @Override
        public Object populateNewBean(String[] line)
                throws CsvBeanIntrospectionException, CsvFieldAssignmentException,
                CsvChainedException {
            return new Object();
        }

        @Override
        public void setType(Class<?> type) {
        }

        @Override
        public String[] transmuteBean(Object bean)
                throws CsvFieldAssignmentException, CsvChainedException {
            return new String[0];
        }
    }
}
