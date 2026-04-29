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
import java.net.URL;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.util.Objects;
import java.util.logging.Level;

import org.jboss.logmanager.ExtLogRecord;
import org.jboss.logmanager.formatters.PatternFormatter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FormattersAnonymous12Test {

    @Test
    void extendedExceptionFormattingUsesTcclDefinedClassAndResourceLookupWhenCodeSourceLocationIsMissing()
            throws Exception {
        final String className = FormattersAnonymous12DefinedClass.class.getName();
        final TrackingDefinedClassLoader trackingLoader = new TrackingDefinedClassLoader(className);

        final String formatted = formatWithTccl(trackingLoader, newFailure(className));

        assertThat(formatted).contains("\tat " + className + ".invoke");
        assertThat(trackingLoader.wasResourceRequested()).isTrue();
    }

    @Test
    void oneArgumentClassForNameIsUsedWhenTheContextClassLoaderRejectsTheFrameClass() {
        final String className = String.class.getName();
        final RejectingClassLoader rejectingLoader = new RejectingClassLoader(
                Thread.currentThread().getContextClassLoader(),
                className
        );

        final String formatted = formatWithTccl(rejectingLoader, newFailure(className));

        assertThat(formatted).contains("\tat " + className + ".invoke");
        assertThat(rejectingLoader.wasRejected()).isTrue();
    }

    @Test
    void bootstrapClassLoaderFallbackIsUsedWhenTheLibraryLoaderRejectsTheFrameClass() throws Throwable {
        final String className = "java.util.HexFormat";
        final BootstrapFallbackClassLoader bootstrapFallbackClassLoader = new BootstrapFallbackClassLoader(className);
        final BootstrapFormattingAction formattingAction = bootstrapFallbackClassLoader.loadFormattingAction();

        final String formatted = formattingAction.format(newFailure(className));

        assertThat(formatted).contains("\tat " + className + ".invoke");
        assertThat(bootstrapFallbackClassLoader.wasRejected()).isTrue();
    }

    private static String formatWithTccl(final ClassLoader contextClassLoader, final Throwable thrown) {
        final ClassLoader originalTccl = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(contextClassLoader);
            final PatternFormatter formatter = new PatternFormatter("%E");
            final ExtLogRecord record = new ExtLogRecord(
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
        final IllegalStateException failure = new IllegalStateException("boom");
        failure.setStackTrace(new StackTraceElement[] {
                new StackTraceElement(className, "invoke", "GeneratedFrame.java", 17)
        });
        return failure;
    }

    private static final class TrackingDefinedClassLoader extends ClassLoader {
        private final String definedClassName;
        private final byte[] definedClassBytes;
        private final String resourceName;
        private boolean resourceRequested;

        private TrackingDefinedClassLoader(final String definedClassName) throws IOException {
            super(FormattersAnonymous12Test.class.getClassLoader());
            this.definedClassName = definedClassName;
            this.resourceName = definedClassName.replace('.', '/') + ".class";
            this.definedClassBytes = loadClassBytes(resourceName);
        }

        boolean wasResourceRequested() {
            return resourceRequested;
        }

        @Override
        protected Class<?> loadClass(final String name, final boolean resolve) throws ClassNotFoundException {
            if (!definedClassName.equals(name)) {
                return super.loadClass(name, resolve);
            }
            synchronized (getClassLoadingLock(name)) {
                final Class<?> alreadyLoaded = findLoadedClass(name);
                if (alreadyLoaded != null) {
                    return alreadyLoaded;
                }
                final Class<?> definedClass = defineClass(
                        name,
                        definedClassBytes,
                        0,
                        definedClassBytes.length,
                        new ProtectionDomain(new CodeSource(null, (Certificate[]) null), null)
                );
                if (resolve) {
                    resolveClass(definedClass);
                }
                return definedClass;
            }
        }

        @Override
        public URL getResource(final String name) {
            if (resourceName.equals(name)) {
                resourceRequested = true;
            }
            return super.getResource(name);
        }

        private static byte[] loadClassBytes(final String resourceName) throws IOException {
            try (InputStream inputStream = FormattersAnonymous12DefinedClass.class.getClassLoader()
                    .getResourceAsStream(resourceName)) {
                return Objects.requireNonNull(inputStream, resourceName).readAllBytes();
            }
        }
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
            final ClassLoader originalTccl = Thread.currentThread().getContextClassLoader();
            try {
                Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
                final PatternFormatter formatter = new PatternFormatter("%E");
                final java.util.logging.LogRecord record = new java.util.logging.LogRecord(Level.SEVERE, "coverage");
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
        private boolean rejected;

        private BootstrapFallbackClassLoader(final String rejectedClassName) {
            super(FormattersAnonymous12Test.class.getClassLoader());
            this.rejectedClassName = rejectedClassName;
        }

        BootstrapFormattingAction loadFormattingAction() throws Throwable {
            final Class<?> actionClass = loadClass(BootstrapFormattingInvoker.class.getName());
            final VarHandle instanceHandle = MethodHandles.publicLookup().findStaticVarHandle(
                    actionClass,
                    "INSTANCE",
                    BootstrapFormattingAction.class
            );
            return BootstrapFormattingAction.class.cast(instanceHandle.get());
        }

        boolean wasRejected() {
            return rejected;
        }

        @Override
        protected Class<?> loadClass(final String name, final boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                final Class<?> alreadyLoaded = findLoadedClass(name);
                if (alreadyLoaded != null) {
                    return alreadyLoaded;
                }
                if (rejectedClassName.equals(name)) {
                    rejected = true;
                    throw new ClassNotFoundException(name);
                }
                if (shouldDefineLocally(name)) {
                    final Class<?> localClass = findClass(name);
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
            final String resourceName = name.replace('.', '/') + ".class";
            try (InputStream inputStream = FormattersAnonymous12Test.class.getClassLoader()
                    .getResourceAsStream(resourceName)) {
                final byte[] classBytes = Objects.requireNonNull(inputStream, resourceName).readAllBytes();
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

final class FormattersAnonymous12DefinedClass {
}
