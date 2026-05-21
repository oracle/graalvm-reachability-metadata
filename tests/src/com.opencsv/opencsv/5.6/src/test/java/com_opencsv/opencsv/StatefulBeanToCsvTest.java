/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import com.opencsv.CSVReader;
import com.opencsv.bean.MappingStrategy;
import com.opencsv.bean.StatefulBeanToCsv;
import com.opencsv.bean.StatefulBeanToCsvBuilder;
import com.opencsv.exceptions.CsvBadConverterException;
import com.opencsv.exceptions.CsvBeanIntrospectionException;
import com.opencsv.exceptions.CsvChainedException;
import com.opencsv.exceptions.CsvFieldAssignmentException;
import com.opencsv.exceptions.CsvRequiredFieldEmptyException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class StatefulBeanToCsvTest {
    private static final String UNRECOVERABLE_WRITING_ERROR =
            "There was an unrecoverable error while writing beans.";

    @Test
    void wrapsUnrecoverableWorkerFailureWithConfiguredLocale() {
        StatefulBeanToCsv<SimpleBean> beanToCsv = new StatefulBeanToCsvBuilder<SimpleBean>(new StringWriter())
                .withMappingStrategy(new ThrowingMappingStrategy())
                .withOrderedResults(false)
                .withErrorLocale(Locale.US)
                .build();

        assertThatThrownBy(() -> beanToCsv.write(List.of(new SimpleBean("alpha"))))
                .isInstanceOf(RuntimeException.class)
                .hasMessage(UNRECOVERABLE_WRITING_ERROR)
                .hasCauseInstanceOf(IOException.class);
    }

    @Test
    void wrapsIteratorFailureWithConfiguredLocale() {
        StatefulBeanToCsv<SimpleBean> beanToCsv = new StatefulBeanToCsvBuilder<SimpleBean>(new StringWriter())
                .withMappingStrategy(new SimpleMappingStrategy())
                .withOrderedResults(false)
                .withErrorLocale(Locale.US)
                .build();

        assertThatThrownBy(() -> beanToCsv.write(new FailingAfterFirstBeanIterator()))
                .isInstanceOf(RuntimeException.class)
                .hasMessage(UNRECOVERABLE_WRITING_ERROR)
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    private static class FailingAfterFirstBeanIterator implements Iterator<SimpleBean> {
        private boolean firstBeanAvailable = true;

        @Override
        public boolean hasNext() {
            if (firstBeanAvailable) {
                return true;
            }
            throw new IllegalStateException("iterator failed after the first bean");
        }

        @Override
        public SimpleBean next() {
            firstBeanAvailable = false;
            return new SimpleBean("beta");
        }
    }

    private static class SimpleMappingStrategy implements MappingStrategy<SimpleBean> {
        @Override
        public void captureHeader(CSVReader reader) throws IOException, CsvRequiredFieldEmptyException {
        }

        @Override
        public String[] generateHeader(SimpleBean bean) throws CsvRequiredFieldEmptyException {
            return new String[0];
        }

        @Override
        public SimpleBean populateNewBean(String[] line)
                throws CsvBeanIntrospectionException, CsvFieldAssignmentException, CsvChainedException {
            return new SimpleBean(line[0]);
        }

        @Override
        public void setType(Class<? extends SimpleBean> type) throws CsvBadConverterException {
        }

        @Override
        public String[] transmuteBean(SimpleBean bean) throws CsvFieldAssignmentException, CsvChainedException {
            return new String[] {bean.value};
        }
    }

    private static final class ThrowingMappingStrategy extends SimpleMappingStrategy {
        @Override
        public String[] transmuteBean(SimpleBean bean) throws CsvFieldAssignmentException, CsvChainedException {
            return sneakyThrow(new IOException("worker conversion failed"));
        }
    }

    public static class SimpleBean {
        private final String value;

        public SimpleBean(String value) {
            this.value = value;
        }
    }

    @SuppressWarnings("unchecked")
    private static <R, T extends Throwable> R sneakyThrow(Throwable throwable) throws T {
        throw (T) throwable;
    }
}
