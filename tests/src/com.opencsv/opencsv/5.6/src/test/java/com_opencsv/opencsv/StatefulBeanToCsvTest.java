/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.opencsv.bean.MappingStrategy;
import com.opencsv.bean.StatefulBeanToCsv;
import com.opencsv.bean.exceptionhandler.ExceptionHandlerThrow;
import com.opencsv.exceptions.CsvBeanIntrospectionException;
import com.opencsv.exceptions.CsvChainedException;
import com.opencsv.exceptions.CsvConstraintViolationException;
import com.opencsv.exceptions.CsvFieldAssignmentException;
import com.opencsv.exceptions.CsvRequiredFieldEmptyException;
import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.Locale;
import java.util.NoSuchElementException;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.junit.jupiter.api.Test;

public class StatefulBeanToCsvTest {
    @Test
    void writeIteratorWrapsUnhandledWorkerCsvException() {
        StatefulBeanToCsv<TestBean> beanToCsv = beanToCsv(new ConstraintViolationMappingStrategy());
        beanToCsv.setErrorLocale(Locale.ROOT);

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> beanToCsv.write(new SingleBeanIterator(new TestBean("blocked"))));

        assertEquals("There was an unrecoverable error while writing beans.", exception.getMessage());
        CsvConstraintViolationException cause = assertInstanceOf(
                CsvConstraintViolationException.class,
                exception.getCause());
        assertEquals("constraint violated", cause.getMessage());
    }

    @Test
    void writeIteratorWrapsInterruptedCompletion() {
        StatefulBeanToCsv<TestBean> beanToCsv = beanToCsv(new PassingMappingStrategy());
        beanToCsv.setErrorLocale(Locale.ROOT);

        Thread.currentThread().interrupt();
        try {
            RuntimeException exception = assertThrows(
                    RuntimeException.class,
                    () -> beanToCsv.write(new SingleBeanIterator(new TestBean("first"))));

            assertEquals("There was an unrecoverable error while writing beans.", exception.getMessage());
            assertInstanceOf(InterruptedException.class, exception.getCause());
        } finally {
            Thread.interrupted();
        }
    }

    private static StatefulBeanToCsv<TestBean> beanToCsv(MappingStrategy<TestBean> mappingStrategy) {
        MultiValuedMap<Class<?>, Field> ignoredFields = new ArrayListValuedHashMap<>();
        return new StatefulBeanToCsv<>(
                mappingStrategy,
                new ExceptionHandlerThrow(),
                false,
                new CSVWriter(new StringWriter()),
                ignoredFields,
                "");
    }

    private static final class TestBean {
        private final String value;

        private TestBean(String value) {
            this.value = value;
        }
    }

    private abstract static class TestBeanMappingStrategy implements MappingStrategy<TestBean> {
        @Override
        public void captureHeader(CSVReader reader) throws IOException, CsvRequiredFieldEmptyException {
        }

        @Override
        public String[] generateHeader(TestBean bean) throws CsvRequiredFieldEmptyException {
            return new String[0];
        }

        @Override
        public TestBean populateNewBean(String[] line)
                throws CsvBeanIntrospectionException, CsvFieldAssignmentException, CsvChainedException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setType(Class<? extends TestBean> type) {
        }
    }

    private static final class ConstraintViolationMappingStrategy extends TestBeanMappingStrategy {
        @Override
        public String[] transmuteBean(TestBean bean) throws CsvFieldAssignmentException, CsvChainedException {
            throw new CsvConstraintViolationException(bean, "constraint violated");
        }
    }

    private static final class PassingMappingStrategy extends TestBeanMappingStrategy {
        @Override
        public String[] transmuteBean(TestBean bean) throws CsvFieldAssignmentException, CsvChainedException {
            return new String[] {bean.value};
        }
    }

    private static final class SingleBeanIterator implements Iterator<TestBean> {
        private TestBean bean;

        private SingleBeanIterator(TestBean bean) {
            this.bean = bean;
        }

        @Override
        public boolean hasNext() {
            return bean != null;
        }

        @Override
        public TestBean next() {
            if (bean == null) {
                throw new NoSuchElementException();
            }
            TestBean nextBean = bean;
            bean = null;
            return nextBean;
        }
    }
}
