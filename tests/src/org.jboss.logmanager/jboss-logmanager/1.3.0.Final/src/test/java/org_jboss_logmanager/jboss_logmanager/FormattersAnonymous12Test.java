/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_logmanager.jboss_logmanager;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.graalvm.internal.tck.NativeImageSupport;
import org.jboss.logmanager.formatters.PatternFormatter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FormattersAnonymous12Test {
    private static final String BOOTSTRAP_FRAME_CLASS = String.class.getName();
    private static final String BOOTSTRAP_FALLBACK_FRAME_CLASS = StackWalker.class.getName();

    @Test
    void extendedExceptionFormattingUsesThreadContextClassLoaderForBootstrapFrameClass() {
        String className = BOOTSTRAP_FRAME_CLASS;

        String formatted = formatWithTccl(FormattersAnonymous12Test.class.getClassLoader(), newFailure(className));

        assertThat(formatted).contains("\tat " + className + ".invoke");
    }

    @Test
    void extendedExceptionFormattingFallsBackToDefaultClassLookupWhenThreadContextLoaderCannotLoadFrameClass() {
        String className = FormattersAnonymous12Test.class.getName();
        ClassLoader rejectingClassLoader = new RejectingClassLoader(className);

        String formatted = formatWithTccl(rejectingClassLoader, newFailure(className));

        assertThat(formatted).contains("\tat " + className + ".invoke");
    }

    @Test
    void extendedExceptionFormattingAttemptsBootstrapLookupWhenOtherClassLookupsFail() {
        String className = "missing.example.FrameClass";
        ClassLoader rejectingClassLoader = new RejectingClassLoader(className);

        String formatted = formatWithTccl(rejectingClassLoader, newFailure(className));

        assertThat(formatted).contains("\tat " + className + ".invoke");
    }

    @Test
    void extendedExceptionFormattingUsesBootstrapLookupWhenFormatterLoaderRejectsFrameClass() throws Exception {
        try (IsolatedLogManagerClassLoader classLoader = new IsolatedLogManagerClassLoader(libraryUrls())) {
            Class<?> formatterClass = classLoader.loadClass(PatternFormatter.class.getName());
            Formatter formatter = (Formatter) formatterClass.getConstructor(String.class).newInstance("%E");

            String formatted = formatWithTccl(null, formatter, newFailure(BOOTSTRAP_FALLBACK_FRAME_CLASS));

            assertThat(formatted).contains("\tat " + BOOTSTRAP_FALLBACK_FRAME_CLASS + ".invoke");
            if (formatterClass.getClassLoader() != classLoader) {
                assertThat(System.getProperty("org.graalvm.nativeimage.imagecode")).isEqualTo("runtime");
            } else {
                assertThat(classLoader.rejectedBootstrapLookupCount()).isPositive();
            }
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static String formatWithTccl(final ClassLoader contextClassLoader, final Throwable thrown) {
        return formatWithTccl(contextClassLoader, new PatternFormatter("%E"), thrown);
    }

    private static String formatWithTccl(
            final ClassLoader contextClassLoader,
            final Formatter formatter,
            final Throwable thrown
    ) {
        ClassLoader originalTccl = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(contextClassLoader);
            LogRecord record = new LogRecord(Level.SEVERE, "coverage");
            record.setLoggerName(FormattersAnonymous12Test.class.getName());
            record.setThrown(thrown);
            return formatter.format(record);
        } finally {
            Thread.currentThread().setContextClassLoader(originalTccl);
        }
    }

    private static URL[] libraryUrls() {
        return new URL[] {PatternFormatter.class.getProtectionDomain().getCodeSource().getLocation() };
    }

    private static IllegalStateException newFailure(final String className) {
        IllegalStateException failure = new IllegalStateException("boom");
        failure.setStackTrace(new StackTraceElement[] {
                new StackTraceElement(className, "invoke", "GeneratedFrame.java", 17)
        });
        return failure;
    }

    private static final class IsolatedLogManagerClassLoader extends URLClassLoader {
        private int rejectedBootstrapLookupCount;

        private IsolatedLogManagerClassLoader(final URL[] urls) {
            super(urls, FormattersAnonymous12Test.class.getClassLoader());
        }

        @Override
        protected Class<?> loadClass(final String name, final boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                if (BOOTSTRAP_FALLBACK_FRAME_CLASS.equals(name) && isGuessClassLookup()) {
                    rejectedBootstrapLookupCount++;
                    throw new ClassNotFoundException(name);
                }
                Class<?> loadedClass = findLoadedClass(name);
                if (loadedClass == null && name.startsWith("org.jboss.logmanager.")) {
                    try {
                        loadedClass = findClass(name);
                    } catch (ClassNotFoundException ignored) {
                        // Delegate to the parent class loader below.
                    }
                }
                if (loadedClass == null) {
                    loadedClass = super.loadClass(name, false);
                }
                if (resolve) {
                    resolveClass(loadedClass);
                }
                return loadedClass;
            }
        }

        private int rejectedBootstrapLookupCount() {
            return rejectedBootstrapLookupCount;
        }

        private boolean isGuessClassLookup() {
            for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
                if ("guessClass".equals(element.getMethodName())
                        && element.getClassName().startsWith("org.jboss.logmanager.formatters.Formatters")) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final class RejectingClassLoader extends ClassLoader {
        private final String rejectedClassName;

        private RejectingClassLoader(final String rejectedClassName) {
            super(FormattersAnonymous12Test.class.getClassLoader());
            this.rejectedClassName = rejectedClassName;
        }

        @Override
        protected Class<?> loadClass(final String name, final boolean resolve) throws ClassNotFoundException {
            if (rejectedClassName.equals(name)) {
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name, resolve);
        }
    }
}
