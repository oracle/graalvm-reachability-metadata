/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_angus.angus_mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.eclipse.angus.mail.util.logging.CollectorFormatter;
import org.eclipse.angus.mail.util.logging.CompactFormatter;
import org.eclipse.angus.mail.util.logging.DurationFilter;
import org.eclipse.angus.mail.util.logging.MailHandler;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
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
    private static final String CHILD_ONLY_FORMATTER_NAME = "org.example.ChildOnlyFormatter";
    private static final String ISOLATED_EXERCISE_NAME =
        "org.example.IsolatedCollectorFormatterExercise";
    private static final String ANGUS_LOGGING_PACKAGE = "org.eclipse.angus.mail.util.logging.";

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

    private static final byte[] CHILD_ONLY_FORMATTER_BYTES = Base64.getDecoder().decode("""
        yv66vgAAAEEAKAoAAgADBwAEDAAFAAYBABtqYXZhL3V0aWwvbG9nZ2luZy9Gb3JtYXR0ZXIB
        AAY8aW5pdD4BAAMoKVYKAAgACQcACgwACwAMAQAbamF2YS91dGlsL2xvZ2dpbmcvTG9nUmVj
        b3JkAQAKZ2V0TWVzc2FnZQEAFCgpTGphdmEvbGFuZy9TdHJpbmc7EgAAAA4MAA8AEAEAF21h
        a2VDb25jYXRXaXRoQ29uc3RhbnRzAQAmKExqYXZhL2xhbmcvU3RyaW5nOylMamF2YS9sYW5n
        L1N0cmluZzsHABIBAB5vcmcvZXhhbXBsZS9DaGlsZE9ubHlGb3JtYXR0ZXIBAARDb2RlAQAP
        TGluZU51bWJlclRhYmxlAQAGZm9ybWF0AQAxKExqYXZhL3V0aWwvbG9nZ2luZy9Mb2dSZWNv
        cmQ7KUxqYXZhL2xhbmcvU3RyaW5nOwEAClNvdXJjZUZpbGUBABdDaGlsZE9ubHlGb3JtYXR0
        ZXIuamF2YQEAEEJvb3RzdHJhcE1ldGhvZHMIABsBAAdjaGlsZDoBDwYAHQoAHgAfBwAgDAAP
        ACEBACRqYXZhL2xhbmcvaW52b2tlL1N0cmluZ0NvbmNhdEZhY3RvcnkBAJgoTGphdmEvbGFu
        Zy9pbnZva2UvTWV0aG9kSGFuZGxlcyRMb29rdXA7TGphdmEvbGFuZy9TdHJpbmc7TGphdmEv
        bGFuZy9pbnZva2UvTWV0aG9kVHlwZTtMamF2YS9sYW5nL1N0cmluZztbTGphdmEvbGFuZy9P
        YmplY3Q7KUxqYXZhL2xhbmcvaW52b2tlL0NhbGxTaXRlOwEADElubmVyQ2xhc3NlcwcAJAEA
        JWphdmEvbGFuZy9pbnZva2UvTWV0aG9kSGFuZGxlcyRMb29rdXAHACYBAB5qYXZhL2xhbmcva
        W52b2tlL01ldGhvZEhhbmRsZXMBAAZMb29rdXAAMQARAAIAAAAAAAIAAQAFAAYAAQATAAAAIQAB
        AAEAAAAFKrcAAbEAAAABABQAAAAKAAIAAAAHAAQACAABABUAFgABABMAAAAiAAEAAgAAAAor
        tgAHugANAACwAAAAAQAUAAAABgABAAAADAADABcAAAACABgAGQAAAAgAAQAcAAEAGgAiAAAA
        CgABACMAJQAnABk=
        """.replaceAll("\\s", ""));

    private static final byte[] ISOLATED_EXERCISE_BYTES = Base64.getDecoder().decode("""
        yv66vgAAAEEAbgoAAgADBwAEDAAFAAYBABBqYXZhL2xhbmcvT2JqZWN0AQAGPGluaXQ+AQAD
        KClWCAAIAQD1LmxldmVsPUlORk8Kb3JnLmVjbGlwc2UuYW5ndXMubWFpbC51dGlsLmxvZ2dp
        bmcuQ29sbGVjdG9yRm9ybWF0dGVyLmZvcm1hdD17MX0Kb3JnLmVjbGlwc2UuYW5ndXMubWFp
        bC51dGlsLmxvZ2dpbmcuQ29sbGVjdG9yRm9ybWF0dGVyLmZvcm1hdHRlcj1vcmcuZXhhbXBs
        ZS5DaGlsZE9ubHlGb3JtYXR0ZXIKb3JnLmVjbGlwc2UuYW5ndXMubWFpbC51dGlsLmxvZ2dp
        bmcuQ29sbGVjdG9yRm9ybWF0dGVyLmNvbXBhcmF0b3I9bnVsbAoHAAoBABxqYXZhL2lvL0J5
        dGVBcnJheUlucHV0U3RyZWFtCQAMAA0HAA4MAA8AEAEAIWphdmEvbmlvL2NoYXJzZXQvU3Rh
        bmRhcmRDaGFyc2V0cwEACklTT184ODU5XzEBABpMamF2YS9uaW8vY2hhcnNldC9DaGFyc2V0
        OwoAEgATBwAUDAAVABYBABBqYXZhL2xhbmcvU3RyaW5nAQAIZ2V0Qnl0ZXMBAB4oTGphdmEv
        bmlvL2NoYXJzZXQvQ2hhcnNldDspW0IKAAkAGAwABQAZAQAFKFtCKVYKABsAHAcAHQwAHgAf
        AQAcamF2YS91dGlsL2xvZ2dpbmcvTG9nTWFuYWdlcgEADWdldExvZ01hbmFnZXIBACAoKUxq
        YXZhL3V0aWwvbG9nZ2luZy9Mb2dNYW5hZ2VyOwoAGwAhDAAiACMBABFyZWFkQ29uZmlndXJh
        dGlvbgEAGChMamF2YS9pby9JbnB1dFN0cmVhbTspVgoAJQAmBwAnDAAoAAYBABNqYXZhL2lv
        L0lucHV0U3RyZWFtAQAFY2xvc2UHACoBABNqYXZhL2xhbmcvVGhyb3dhYmxlCgApACwMAC0A
        LgEADWFkZFN1cHByZXNzZWQBABgoTGphdmEvbGFuZy9UaHJvd2FibGU7KVYHADABABNqYXZh
        L2xhbmcvRXhjZXB0aW9uBwAyAQAYamF2YS9sYW5nL0Fzc2VydGlvbkVycm9yCgAxADQMAAUA
        NQEAFShMamF2YS9sYW5nL09iamVjdDspVgoANwA4BwA5DAA6ADsBABBqYXZhL2xhbmcvVGhy
        ZWFkAQANY3VycmVudFRocmVhZAEAFCgpTGphdmEvbGFuZy9UaHJlYWQ7CgA3AD0MAD4APwEA
        FWdldENvbnRleHRDbGFzc0xvYWRlcgEAGSgpTGphdmEvbGFuZy9DbGFzc0xvYWRlcjsKADcA
        QQwAQgBDAQAVc2V0Q29udGV4dENsYXNzTG9hZGVyAQAaKExqYXZhL2xhbmcvQ2xhc3NMb2Fk
        ZXI7KVYHAEUBADZvcmcvZWNsaXBzZS9hbmd1cy9tYWlsL3V0aWwvbG9nZ2luZy9Db2xsZWN0
        b3JGb3JtYXR0ZXIKAEQAAwcASAEAG2phdmEvdXRpbC9sb2dnaW5nL0xvZ1JlY29yZAkASgBL
        BwBMDABNAE4BABdqYXZhL3V0aWwvbG9nZ2luZy9MZXZlbAEABElORk8BABlMamF2YS91dGls
        L2xvZ2dpbmcvTGV2ZWw7CABQAQARaXNvbGF0ZWQgZmFsbGJhY2sKAEcAUgwABQBTAQAuKExq
        YXZhL3V0aWwvbG9nZ2luZy9MZXZlbDtMamF2YS9sYW5nL1N0cmluZzspVgoARABVDABWAFcB
        AAZmb3JtYXQBADEoTGphdmEvdXRpbC9sb2dnaW5nL0xvZ1JlY29yZDspTGphdmEvbGFuZy9T
        dHJpbmc7CgBEAFkMAFoAWwEAB2dldFRhaWwBAC8oTGphdmEvdXRpbC9sb2dnaW5nL0hhbmRs
        ZXI7KUxqYXZhL2xhbmcvU3RyaW5nOwgAXQEAF2NoaWxkOmlzb2xhdGVkIGZhbGxiYWNrCgAS
        AF8MAGAAYQEACGNvbnRhaW5zAQAbKExqYXZhL2xhbmcvQ2hhclNlcXVlbmNlOylaBwBjAQAu
        b3JnL2V4YW1wbGUvSXNvbGF0ZWRDb2xsZWN0b3JGb3JtYXR0ZXJFeGVyY2lzZQcAZQEAEmph
        dmEvbGFuZy9SdW5uYWJsZQEABENvZGUBAA9MaW5lTnVtYmVyVGFibGUBAANydW4BAA1TdGFj
        a01hcFRhYmxlBwBrAQAVamF2YS9sYW5nL0NsYXNzTG9hZGVyAQAKU291cmNlRmlsZQEAJ0lz
        b2xhdGVkQ29sbGVjdG9yRm9ybWF0dGVyRXhlcmNpc2UuamF2YQAxAGIAAgABAGQAAAACAAEA
        BQAGAAEAZgAAACEAAQABAAAABSq3AAGxAAAAAQBnAAAACgACAAAADAAEAA0AAQBoAAYAAQBm
        AAABbwAFAAcAAACXEgdMuwAJWSuyAAu2ABG3ABdNuAAaLLYAICy2ACSnABVOLLYAJKcACzoE
        LRkEtgArLb+nAA1NuwAxWSy3ADO/uAA2TSy2ADxOLAG2AEC7AERZtwBGOgQZBLsAR1myAEkS
        T7cAUbYAVFcZBAG2AFg6BRkFEly2AF6aAA27ADFZGQW3ADO/LC22AECnAA06BiwttgBAGQa/
        sQAFABIAGQAgACkAIQAlACgAKQADADIANQAvAE0AhACMAAAAjACOAIwAAAACAGcAAABWABUA
        AAARAAMAFwASABgAGQAZACAAFwAyABsANQAZADYAGgA/AB0AQwAeAEgAHwBNACEAVgAiAGgA
        IwBwACQAegAlAIQAKACJACkAjAAoAJMAKQCWACoAaQAAAEIACf8AIAADBwBiBwASBwAlAAEH
        ACn/AAcABAcAYgcAEgcAJQcAKQABBwApB/kAAUIHAC8J/QBEBwA3BwBqRwcAKQkAAQBsAAAA
        AgBt
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
            "org.eclipse.angus.mail.util.logging.DurationFilter.records=2",
            "org.eclipse.angus.mail.util.logging.DurationFilter.duration=PT0.001S");

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
            "org.eclipse.angus.mail.util.logging.CollectorFormatter.format={1}",
            "org.eclipse.angus.mail.util.logging.CollectorFormatter.formatter="
                + "org.eclipse.angus.mail.util.logging.CompactFormatter",
            "org.eclipse.angus.mail.util.logging.CollectorFormatter.comparator="
                + "org.eclipse.angus.mail.util.logging.SeverityComparator",
            "org.eclipse.angus.mail.util.logging.CollectorFormatter.comparator.reverse=true",
            "org.eclipse.angus.mail.util.logging.CompactFormatter.format=%4$s:%5$s%n");

        CollectorFormatter formatter = new CollectorFormatter();
        formatter.format(new LogRecord(Level.SEVERE, "severe message"));
        formatter.format(new LogRecord(Level.INFO, "info message"));

        String tail = formatter.getTail(null);

        assertThat(tail).contains("INFO:info message");
    }

    @Test
    public void collectorFormatterLoadsFormatterThroughContextClassLoader() throws Exception {
        configureLogManager(
            "org.eclipse.angus.mail.util.logging.CollectorFormatter.format={1}",
            "org.eclipse.angus.mail.util.logging.CollectorFormatter.formatter=" + CONTEXT_FORMATTER_NAME,
            "org.eclipse.angus.mail.util.logging.CollectorFormatter.comparator=null");

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
            "org.eclipse.angus.mail.util.logging.CollectorFormatter.format={1}",
            "org.eclipse.angus.mail.util.logging.CollectorFormatter.formatter=java.util.logging.SimpleFormatter",
            "org.eclipse.angus.mail.util.logging.CollectorFormatter.comparator=null");

        CollectorFormatter formatter = withContextClassLoader(null, CollectorFormatter::new);
        formatter.format(new LogRecord(Level.INFO, "simple formatter fallback"));

        assertThat(formatter.getTail(null)).contains("simple formatter fallback");
    }

    @Test
    public void isolatedCollectorFormatterUsesDefiningLoaderWhenContextClassLoaderIsUnset()
            throws Exception {
        try {
            Runnable exercise = newIsolatedCollectorFormatterExercise();
            exercise.run();
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    @Test
    public void collectorFormatterLoadsCallerVisibleFormatterWhenContextClassLoaderIsUnset()
            throws Exception {
        configureLogManager(
            "org.eclipse.angus.mail.util.logging.CollectorFormatter.format={1}",
            "org.eclipse.angus.mail.util.logging.CollectorFormatter.formatter="
                + ClassForNameFallbackFormatter.class.getName(),
            "org.eclipse.angus.mail.util.logging.CollectorFormatter.comparator=null");

        CollectorFormatter formatter = withContextClassLoader(null, CollectorFormatter::new);
        formatter.format(new LogRecord(Level.INFO, "caller fallback"));

        assertThat(formatter.getTail(null)).contains("fallback:caller fallback");
    }

    @Test
    public void collectorFormatterFailsWhenContextClassLoaderIsUnset() throws Exception {
        configureLogManager(
            "org.eclipse.angus.mail.util.logging.CollectorFormatter.formatter="
                + "org.example.DoesNotExist");

        UndeclaredThrowableException thrown = withContextClassLoader(null,
            () -> assertThrows(UndeclaredThrowableException.class, CollectorFormatter::new));

        assertThat(thrown.getUndeclaredThrowable()).isInstanceOf(ClassNotFoundException.class);
    }

    private static Runnable newIsolatedCollectorFormatterExercise() throws Exception {
        IsolatedAngusClassLoader loader = new IsolatedAngusClassLoader();
        try {
            return Runnable.class.cast(loader.loadClass(ISOLATED_EXERCISE_NAME)
                .getConstructor()
                .newInstance());
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Exception checked) {
                throw checked;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw exception;
        }
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

    private static final class IsolatedAngusClassLoader extends ClassLoader {
        private IsolatedAngusClassLoader() {
            super(ClassLoader.getPlatformClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    if (isIsolatedClassName(name)) {
                        try {
                            loaded = findClass(name);
                        } catch (ClassNotFoundException notIsolated) {
                            loaded = super.loadClass(name, false);
                        }
                    } else {
                        loaded = super.loadClass(name, false);
                    }
                }
                if (resolve) {
                    resolveClass(loaded);
                }
                return loaded;
            }
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            byte[] bytes;
            if (CHILD_ONLY_FORMATTER_NAME.equals(name)) {
                bytes = CHILD_ONLY_FORMATTER_BYTES;
            } else if (ISOLATED_EXERCISE_NAME.equals(name)) {
                bytes = ISOLATED_EXERCISE_BYTES;
            } else if (name.startsWith(ANGUS_LOGGING_PACKAGE)) {
                bytes = readClassBytes(name);
            } else {
                throw new ClassNotFoundException(name);
            }
            return defineClass(name, bytes, 0, bytes.length);
        }

        private static boolean isIsolatedClassName(String name) {
            return CHILD_ONLY_FORMATTER_NAME.equals(name)
                || ISOLATED_EXERCISE_NAME.equals(name)
                || name.startsWith(ANGUS_LOGGING_PACKAGE);
        }

        private static byte[] readClassBytes(String name) throws ClassNotFoundException {
            String resource = name.replace('.', '/') + ".class";
            ClassLoader loader = LogManagerPropertiesTest.class.getClassLoader();
            InputStream input = loader == null
                ? ClassLoader.getSystemResourceAsStream(resource)
                : loader.getResourceAsStream(resource);
            if (input == null) {
                throw new ClassNotFoundException(name);
            }
            try (input) {
                return input.readAllBytes();
            } catch (IOException exception) {
                ClassNotFoundException failure = new ClassNotFoundException(name);
                failure.initCause(exception);
                throw failure;
            }
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
