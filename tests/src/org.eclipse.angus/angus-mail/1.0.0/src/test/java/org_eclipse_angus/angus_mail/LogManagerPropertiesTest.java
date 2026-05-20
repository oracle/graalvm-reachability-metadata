/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_angus.angus_mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sun.mail.util.logging.CollectorFormatter;
import com.sun.mail.util.logging.CompactFormatter;
import com.sun.mail.util.logging.DurationFilter;
import com.sun.mail.util.logging.MailHandler;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.UndeclaredThrowableException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import org.junit.jupiter.api.Test;

public class LogManagerPropertiesTest {
    @Test
    public void compactFormatterFormatsZonedTimeAndClassifiesStackFrames() {
        LogRecord record = new LogRecord(Level.WARNING, "delivery failed");
        RuntimeException thrown = new RuntimeException("boom");
        thrown.setStackTrace(new StackTraceElement[] {
            new StackTraceElement("java.lang.Math", "abs", "Math.java", 123),
            new StackTraceElement("java.lang.Throwable", "fillInStackTrace", "Throwable.java", 10),
            new StackTraceElement("java.lang.String", "valueOf", "String.java", 42)
        });
        record.setThrown(thrown);

        String formatted = new CompactFormatter("%1$tFT%1$tT %5$s %6$s%n").format(record);

        assertThat(formatted)
            .contains("delivery failed")
            .contains("RuntimeException: boom")
            .contains("Throwable.fillInStackTrace");
    }

    @Test
    public void durationFilterParsesIsoDurationFromLogManager() throws Exception {
        configureLogManager(
            "com.sun.mail.util.logging.DurationFilter.records=2",
            "com.sun.mail.util.logging.DurationFilter.duration=PT0.001S");

        DurationFilter filter = new DurationFilter();
        LogRecord first = new LogRecord(Level.INFO, "first");
        LogRecord second = new LogRecord(Level.INFO, "second");

        assertThat(filter.isLoggable(first)).isTrue();
        assertThat(filter.isLoggable(second)).isTrue();
    }

    @Test
    public void mailHandlerVerifiesLocalHostFromTransport() {
        Properties properties = new Properties();
        properties.setProperty("mail.transport.protocol", "smtp");
        properties.setProperty("mail.smtp.host", "localhost");
        properties.setProperty("mail.smtp.localhost", "localhost");
        properties.setProperty("mail.to", "recipient@example.com");
        properties.setProperty("mail.from", "sender@example.com");
        properties.setProperty("verify", "local");

        MailHandler handler = new MailHandler(properties);
        try {
            assertThat(handler.getLevel()).isNotNull();
        } finally {
            handler.close();
        }
    }

    @Test
    public void collectorFormatterLoadsConfiguredFormatterAndReversesComparator() throws Exception {
        configureLogManager(
            "com.sun.mail.util.logging.CollectorFormatter.format={1}",
            "com.sun.mail.util.logging.CollectorFormatter.formatter="
                + "com.sun.mail.util.logging.CompactFormatter",
            "com.sun.mail.util.logging.CollectorFormatter.comparator="
                + "com.sun.mail.util.logging.SeverityComparator",
            "com.sun.mail.util.logging.CollectorFormatter.comparator.reverse=true",
            "com.sun.mail.util.logging.CompactFormatter.format=%4$s:%5$s%n");

        CollectorFormatter formatter = new CollectorFormatter();
        formatter.format(new LogRecord(Level.SEVERE, "severe message"));
        formatter.format(new LogRecord(Level.INFO, "info message"));

        String tail = formatter.getTail(null);

        assertThat(tail).contains("INFO:info message");
    }

    @Test
    public void collectorFormatterUsesContextClassLoaderBeforeFailing() throws Exception {
        configureLogManager(
            "com.sun.mail.util.logging.CollectorFormatter.format={1}",
            "com.sun.mail.util.logging.CollectorFormatter.formatter=org.example.ContextFormatter",
            "com.sun.mail.util.logging.CollectorFormatter.comparator=null",
            "com.sun.mail.util.logging.CompactFormatter.format=%5$s%n");

        UndeclaredThrowableException thrown = withContextClassLoader(
            new FormatterContextClassLoader(),
            () -> assertThrows(UndeclaredThrowableException.class, CollectorFormatter::new));

        assertThat(thrown.getUndeclaredThrowable()).isInstanceOf(ClassNotFoundException.class);
    }

    @Test
    public void collectorFormatterFailsWhenContextClassLoaderIsUnset() throws Exception {
        configureLogManager(
            "com.sun.mail.util.logging.CollectorFormatter.formatter="
                + "org.example.DoesNotExist");

        UndeclaredThrowableException thrown = withContextClassLoader(null,
            () -> assertThrows(UndeclaredThrowableException.class, CollectorFormatter::new));

        assertThat(thrown.getUndeclaredThrowable()).isInstanceOf(ClassNotFoundException.class);
    }

    private static <T> T withContextClassLoader(ClassLoader loader, ThrowingSupplier<T> supplier)
            throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        thread.setContextClassLoader(loader);
        try {
            return supplier.get();
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    private static void configureLogManager(String... entries) throws IOException {
        StringBuilder builder = new StringBuilder();
        builder.append(".level=INFO\n");
        for (String entry : entries) {
            builder.append(entry).append('\n');
        }

        byte[] bytes = builder.toString().getBytes(StandardCharsets.ISO_8859_1);
        try (InputStream input = new ByteArrayInputStream(bytes)) {
            LogManager.getLogManager().readConfiguration(input);
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    private static final class FormatterContextClassLoader extends ClassLoader {
        private FormatterContextClassLoader() {
            super(null);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if ("org.example.ContextFormatter".equals(name)) {
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name, resolve);
        }
    }
}
