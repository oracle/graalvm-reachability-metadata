/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_logmanager.jboss_logmanager;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.graalvm.internal.tck.NativeImageSupport;
import org.jboss.logmanager.ExtLogRecord;
import org.jboss.logmanager.formatters.PatternFormatter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FormattersAnonymous12Test {

    @Test
    void extendedExceptionFormattingUsesThreadContextClassLoaderForBootstrapFrameClass() {
        String className = String.class.getName();

        String formatted = formatWithTccl(FormattersAnonymous12Test.class.getClassLoader(), newFailure(className));

        assertThat(formatted).contains("\tat " + className + ".invoke");
    }

    @Test
    void extendedExceptionFormattingFallsBackFromContextClassLoaderToFormatterClassLoader() {
        String className = PatternFormatter.class.getName();
        RejectingClassLoader rejectingClassLoader = new RejectingClassLoader(
                FormattersAnonymous12Test.class.getClassLoader(),
                className
        );

        String formatted = formatWithTccl(rejectingClassLoader, newFailure(className));

        assertThat(formatted).contains("\tat " + className + ".invoke");
        if (!isNativeImageRuntime()) {
            assertThat(rejectingClassLoader.wasRejected()).isTrue();
        }
    }

    @Test
    void extendedExceptionFormattingFallsBackToBootstrapClassLoaderWhenOwningLoaderCannotLoadFrameClass()
            throws Throwable {
        try {
            String className = "java.util.HexFormat";
            BootstrapFallbackClassLoader classLoader = new BootstrapFallbackClassLoader(className);
            BootstrapFormattingAction formattingAction = classLoader.loadFormattingAction();

            String formatted = formattingAction.format(newFailure(className));

            assertThat(formatted).contains("\tat " + className + ".invoke");
            if (!isNativeImageRuntime()) {
                assertThat(classLoader.getRejectedCount()).isGreaterThanOrEqualTo(2);
            }
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static String formatWithTccl(final ClassLoader contextClassLoader, final Throwable thrown) {
        ClassLoader originalTccl = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(contextClassLoader);
            PatternFormatter formatter = new PatternFormatter("%E");
            ExtLogRecord record = new ExtLogRecord(
                    Level.SEVERE,
                    "coverage",
                    FormattersAnonymous12Test.class.getName()
            );
            record.setThrown(thrown);
            return formatter.format(record);
        } finally {
            Thread.currentThread().setContextClassLoader(originalTccl);
        }
    }

    private static IllegalStateException newFailure(final String className) {
        IllegalStateException failure = new IllegalStateException("boom");
        failure.setStackTrace(new StackTraceElement[] {
                new StackTraceElement(className, "invoke", "GeneratedFrame.java", 17)
        });
        return failure;
    }

    private static boolean isNativeImageRuntime() {
        return "runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"));
    }

    public interface BootstrapFormattingAction {
        String format(Throwable thrown);
    }

    public static final class BootstrapFormattingInvoker implements BootstrapFormattingAction {
        public static final BootstrapFormattingAction INSTANCE = new BootstrapFormattingInvoker();

        private BootstrapFormattingInvoker() {
        }

        @Override
        public String format(final Throwable thrown) {
            ClassLoader originalTccl = Thread.currentThread().getContextClassLoader();
            try {
                Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
                PatternFormatter formatter = new PatternFormatter("%E");
                LogRecord record = new LogRecord(Level.SEVERE, "coverage");
                record.setThrown(thrown);
                return formatter.format(record);
            } finally {
                Thread.currentThread().setContextClassLoader(originalTccl);
            }
        }
    }

    private static final class RejectingClassLoader extends ClassLoader {
        private final String rejectedClassName;
        private boolean rejected;

        private RejectingClassLoader(final ClassLoader parent, final String rejectedClassName) {
            super(parent);
            this.rejectedClassName = rejectedClassName;
        }

        boolean wasRejected() {
            return rejected;
        }

        @Override
        protected Class<?> loadClass(final String name, final boolean resolve) throws ClassNotFoundException {
            if (rejectedClassName.equals(name)) {
                rejected = true;
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name, resolve);
        }
    }

    private static final class BootstrapFallbackClassLoader extends ClassLoader {
        private final String rejectedClassName;
        private int rejectedCount;

        private BootstrapFallbackClassLoader(final String rejectedClassName) {
            super(FormattersAnonymous12Test.class.getClassLoader());
            this.rejectedClassName = rejectedClassName;
        }

        BootstrapFormattingAction loadFormattingAction() throws Throwable {
            Class<?> actionClass = Class.forName(BootstrapFormattingInvoker.class.getName(), true, this);
            VarHandle instanceHandle = MethodHandles.publicLookup().findStaticVarHandle(
                    actionClass,
                    "INSTANCE",
                    BootstrapFormattingAction.class
            );
            return BootstrapFormattingAction.class.cast(instanceHandle.get());
        }

        int getRejectedCount() {
            return rejectedCount;
        }

        @Override
        protected Class<?> loadClass(final String name, final boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> alreadyLoaded = findLoadedClass(name);
                if (alreadyLoaded != null) {
                    return alreadyLoaded;
                }
                if (rejectedClassName.equals(name)) {
                    rejectedCount++;
                    throw new ClassNotFoundException(name);
                }
                if (shouldDefineLocally(name)) {
                    Class<?> localClass = findClass(name);
                    if (resolve) {
                        resolveClass(localClass);
                    }
                    return localClass;
                }
                return super.loadClass(name, resolve);
            }
        }

        @Override
        protected Class<?> findClass(final String name) throws ClassNotFoundException {
            String resourceName = name.replace('.', '/') + ".class";
            try (InputStream inputStream = FormattersAnonymous12Test.class.getClassLoader()
                    .getResourceAsStream(resourceName)) {
                byte[] classBytes = Objects.requireNonNull(inputStream, resourceName).readAllBytes();
                return defineClass(name, classBytes, 0, classBytes.length);
            } catch (IOException e) {
                throw new ClassNotFoundException(name, e);
            }
        }

        private boolean shouldDefineLocally(final String name) {
            return name.startsWith("org.jboss.logmanager.")
                    || BootstrapFormattingInvoker.class.getName().equals(name);
        }
    }
}
