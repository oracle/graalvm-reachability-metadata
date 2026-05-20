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
import java.util.Base64;
import java.util.Properties;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class LogManagerPropertiesTest {
    private static final String CONTEXT_FORMATTER_NAME = "org.example.ContextFormatter";

    private static final byte[] CONTEXT_FORMATTER_BYTES = Base64.getDecoder().decode("""
        yv66vgAAADQAFQoAAgADBwAEDAAFAAYBABtqYXZhL3V0aWwvbG9nZ2luZy9Gb3JtYXR0ZXIB
        AAY8aW5pdD4BAAMoKVYKAAgACQcACgwACwAMAQAbamF2YS91dGlsL2xvZ2dpbmcvTG9nUmVj
        b3JkAQAKZ2V0TWVzc2FnZQEAFCgpTGphdmEvbGFuZy9TdHJpbmc7BwAOAQAcb3JnL2V4YW1w
        bGUvQ29udGV4dEZvcm1hdHRlcgEABENvZGUBAA9MaW5lTnVtYmVyVGFibGUBAAZmb3JtYXQB
        ADEoTGphdmEvdXRpbC9sb2dnaW5nL0xvZ1JlY29yZDspTGphdmEvbGFuZy9TdHJpbmc7AQAK
        U291cmNlRmlsZQEAFUNvbnRleHRGb3JtYXR0ZXIuamF2YQAhAA0AAgAAAAAAAgABAAUABgAB
        AA8AAAAdAAEAAQAAAAUqtwABsQAAAAEAEAAAAAYAAQAAAAQAAQARABIAAQAPAAAAHQABAAIA
        AAAFK7YAB7AAAAABABAAAAAGAAEAAAAFAAEAEwAAAAIAFA==
        """.replaceAll("\\s", ""));

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
    public void collectorFormatterLoadsFormatterThroughContextClassLoader() throws Exception {
        configureLogManager(
            "com.sun.mail.util.logging.CollectorFormatter.format={1}",
            "com.sun.mail.util.logging.CollectorFormatter.formatter=" + CONTEXT_FORMATTER_NAME,
            "com.sun.mail.util.logging.CollectorFormatter.comparator=null");

        try {
            CollectorFormatter formatter = withContextClassLoader(
                new FormatterContextClassLoader(), CollectorFormatter::new);
            formatter.format(new LogRecord(Level.INFO, "context message"));

            assertThat(formatter.getTail(null)).contains("context message");
        } catch (UndeclaredThrowableException exception) {
            if (isUnsupportedNativeContextFormatterLoad(exception)) {
                return;
            }
            throw exception;
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    @Test
    public void collectorFormatterLoadsJdkFormatterWhenContextClassLoaderIsUnset() throws Exception {
        configureLogManager(
            "com.sun.mail.util.logging.CollectorFormatter.format={1}",
            "com.sun.mail.util.logging.CollectorFormatter.formatter=java.util.logging.SimpleFormatter",
            "com.sun.mail.util.logging.CollectorFormatter.comparator=null");

        CollectorFormatter formatter = withContextClassLoader(null, CollectorFormatter::new);
        formatter.format(new LogRecord(Level.INFO, "simple formatter fallback"));

        assertThat(formatter.getTail(null)).contains("simple formatter fallback");
    }

    @Test
    public void collectorFormatterLoadsCallerVisibleFormatterWhenContextClassLoaderIsUnset()
            throws Exception {
        configureLogManager(
            "com.sun.mail.util.logging.CollectorFormatter.format={1}",
            "com.sun.mail.util.logging.CollectorFormatter.formatter="
                + ClassForNameFallbackFormatter.class.getName(),
            "com.sun.mail.util.logging.CollectorFormatter.comparator=null");

        CollectorFormatter formatter = withContextClassLoader(null, CollectorFormatter::new);
        formatter.format(new LogRecord(Level.INFO, "caller fallback"));

        assertThat(formatter.getTail(null)).contains("fallback:caller fallback");
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

    private static boolean isUnsupportedNativeContextFormatterLoad(Throwable throwable) {
        if (!"runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"))) {
            return false;
        }
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ClassNotFoundException classNotFoundException
                    && CONTEXT_FORMATTER_NAME.equals(classNotFoundException.getMessage())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    public static final class ClassForNameFallbackFormatter extends Formatter {
        @Override
        public String format(LogRecord record) {
            return "fallback:" + record.getMessage();
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    private static final class FormatterContextClassLoader extends ClassLoader {
        private FormatterContextClassLoader() {
            super(ClassLoader.getPlatformClassLoader());
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (CONTEXT_FORMATTER_NAME.equals(name)) {
                return defineClass(name, CONTEXT_FORMATTER_BYTES, 0, CONTEXT_FORMATTER_BYTES.length);
            }
            throw new ClassNotFoundException(name);
        }
    }

}
