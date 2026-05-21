/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import com.opencsv.CSVReader;
import com.opencsv.bean.MappingStrategy;
import com.opencsv.bean.concurrent.BeanExecutor;
import com.opencsv.bean.exceptionhandler.ExceptionHandlerThrow;
import com.opencsv.exceptions.CsvBadConverterException;
import com.opencsv.exceptions.CsvBeanIntrospectionException;
import com.opencsv.exceptions.CsvChainedException;
import com.opencsv.exceptions.CsvDataTypeMismatchException;
import com.opencsv.exceptions.CsvFieldAssignmentException;
import com.opencsv.exceptions.CsvRequiredFieldEmptyException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class IntolerantThreadPoolExecutorTest {
    @Test
    void formatsTerminalCsvExceptionWithErrorLocaleWhenResultsAreRequested() throws Exception {
        BeanExecutor<SimpleBean> executor = new BeanExecutor<>(false, Locale.US);
        executor.prepare();
        try {
            executor.submitBean(7L, new FailingMappingStrategy(), new SimpleBean("alpha"),
                    new ExceptionHandlerThrow());
            assertThat(awaitTerminalException(executor))
                    .isInstanceOf(CsvDataTypeMismatchException.class);

            assertThatThrownBy(() -> executor.tryAdvance(line -> { }))
                    .isInstanceOf(RuntimeException.class)
                    .hasCauseInstanceOf(CsvDataTypeMismatchException.class)
                    .hasMessageContaining("Error parsing CSV line: 7");
        }
        finally {
            executor.shutdownNow();
        }
    }

    private static Throwable awaitTerminalException(BeanExecutor<?> executor) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Throwable terminalException = executor.getTerminalException();
            if (terminalException != null) {
                return terminalException;
            }
            TimeUnit.MILLISECONDS.sleep(10);
        }
        throw new AssertionError("Worker did not report a terminal exception within the timeout");
    }

    private static class FailingMappingStrategy implements MappingStrategy<SimpleBean> {
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
            throw new CsvDataTypeMismatchException(bean.value, Integer.class, "worker conversion failed");
        }
    }

    public static class SimpleBean {
        private final String value;

        public SimpleBean(String value) {
            this.value = value;
        }
    }
}
