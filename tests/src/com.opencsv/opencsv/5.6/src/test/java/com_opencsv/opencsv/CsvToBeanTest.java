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
import java.io.IOException;
import java.io.StringReader;
import java.util.Locale;
import org.junit.jupiter.api.Test;

public class CsvToBeanTest {
    @Test
    void parseRejectsMissingStrategyAndReader() {
        CsvToBean<Object> csvToBean = new CsvToBean<>();
        csvToBean.setErrorLocale(Locale.ROOT);

        assertThrows(IllegalStateException.class, csvToBean::parse);
    }

    @Test
    void parseWrapsHeaderCaptureFailure() throws IOException {
        CsvToBean<Object> csvToBean = new CsvToBean<>();
        csvToBean.setErrorLocale(Locale.ROOT);
        csvToBean.setMappingStrategy(new FailingHeaderMappingStrategy());

        try (CSVReader reader = new CSVReader(new StringReader("value\n"))) {
            csvToBean.setCsvReader(reader);

            RuntimeException exception = assertThrows(RuntimeException.class, csvToBean::parse);

            assertTrue(exception.getCause() instanceof IOException);
            assertEquals("header unavailable", exception.getCause().getMessage());
        }
    }

    private static final class FailingHeaderMappingStrategy implements MappingStrategy<Object> {
        @Override
        public void captureHeader(CSVReader reader) throws IOException {
            throw new IOException("header unavailable");
        }

        @Override
        public String[] generateHeader(Object bean) throws CsvRequiredFieldEmptyException {
            return new String[0];
        }

        @Override
        public Object populateNewBean(String[] line)
                throws CsvBeanIntrospectionException, CsvFieldAssignmentException,
                CsvChainedException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setType(Class<?> type) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String[] transmuteBean(Object bean)
                throws CsvFieldAssignmentException, CsvChainedException {
            throw new UnsupportedOperationException();
        }
    }
}
