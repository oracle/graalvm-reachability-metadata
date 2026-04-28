/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_logmanager.jboss_logmanager;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
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
import static org.assertj.core.api.Assertions.catchThrowable;

public class FormattersAnonymous12Test {

    @Test
    void extendedExceptionFormattingUsesTcclDefinedClassAndResourceLookupWhenCodeSourceLocationIsMissing()
            throws Exception {
        String className = FormattersAnonymous12DefinedClass.class.getName();
        TrackingDefinedClassLoader trackingLoader = new TrackingDefinedClassLoader(className);

        String formatted = formatWithTccl(trackingLoader, newFailure(className));

        assertThat(formatted).contains("\tat " + className + ".invoke");
        boolean resourceLookupReached = trackingLoader.wasResourceRequested()
                || trackingLoader.wasClassDefinitionUnavailable();
        assertThat(resourceLookupReached).isTrue();
    }

    @Test
    void extendedExceptionFormattingUsesThreadContextClassLoaderForBootstrapFrameClass() {
        String className = String.class.getName();

        String formatted = formatWithTccl(FormattersAnonymous12Test.class.getClassLoader(), newFailure(className));

        assertThat(formatted).contains("\tat " + className + ".invoke");
    }

    @Test
    void extendedExceptionFormattingFallsBackToDefaultClassLookupWhenTcclRejectsFrameClass() {
        String className = String.class.getName();
        RejectingClassLoader rejectingClassLoader = new RejectingClassLoader(
                FormattersAnonymous12Test.class.getClassLoader(),
                className
        );

        String formatted = formatWithTccl(rejectingClassLoader, newFailure(className));

        assertThat(formatted).contains("\tat " + className + ".invoke");
        assertThat(rejectingClassLoader.wasRejected()).isTrue();
    }

    @Test
    void extendedExceptionFormattingFallsBackToBootstrapLookupWhenLibraryLoaderRejectsFrameClass() throws Throwable {
        String className = "java.util.HexFormat";
        BootstrapFallbackClassLoader bootstrapFallbackClassLoader = new BootstrapFallbackClassLoader(className);
        MethodHandle[] formattingHandle = new MethodHandle[1];

        Throwable loadFailure = catchThrowable(
                () -> formattingHandle[0] = bootstrapFallbackClassLoader.loadFormattingHandle()
        );
        if (loadFailure != null) {
            String formatted = formatWithTccl(FormattersAnonymous12Test.class.getClassLoader(), newFailure(className));
            assertThat(formatted).contains("\tat " + className + ".invoke");
            return;
        }

        String formatted = (String) formattingHandle[0].invoke(newFailure(className));

        assertThat(formatted).contains("\tat " + className + ".invoke");
        assertThat(bootstrapFallbackClassLoader.rejectedCount()).isGreaterThanOrEqualTo(2);
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

    public static final class BootstrapFormattingInvoker {
        private BootstrapFormattingInvoker() {
        }

        public static String format(final Throwable thrown) {
            ClassLoader originalTccl = Thread.currentThread().getContextClassLoader();
            try {
                Thread.currentThread().setContextClassLoader(BootstrapFormattingInvoker.class.getClassLoader());
                PatternFormatter formatter = new PatternFormatter("%E");
                ExtLogRecord record = new ExtLogRecord(
                        Level.SEVERE,
                        "coverage",
                        BootstrapFormattingInvoker.class.getName()
                );
                record.setThrown(thrown);
                return formatter.format(record);
            } finally {
                Thread.currentThread().setContextClassLoader(originalTccl);
            }
        }
    }

    private static final class TrackingDefinedClassLoader extends ClassLoader {
        private final String definedClassName;
        private final byte[] definedClassBytes;
        private final String resourceName;
        private boolean classDefinitionUnavailable;
        private boolean resourceRequested;

        private TrackingDefinedClassLoader(final String definedClassName) throws IOException {
            super(FormattersAnonymous12Test.class.getClassLoader());
            this.definedClassName = definedClassName;
            this.resourceName = definedClassName.replace('.', '/') + ".class";
            this.definedClassBytes = loadClassBytes(resourceName);
        }

        boolean wasClassDefinitionUnavailable() {
            return classDefinitionUnavailable;
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
                Class<?> alreadyLoaded = findLoadedClass(name);
                if (alreadyLoaded != null) {
                    return alreadyLoaded;
                }
                try {
                    Class<?> definedClass = defineClass(
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
                } catch (UnsupportedOperationException e) {
                    classDefinitionUnavailable = true;
                    throw new ClassNotFoundException(name, e);
                }
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
            ClassLoader testClassLoader = FormattersAnonymous12DefinedClass.class.getClassLoader();
            try (InputStream inputStream = testClassLoader.getResourceAsStream(resourceName)) {
                return Objects.requireNonNull(inputStream, resourceName).readAllBytes();
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
            super(ClassLoader.getPlatformClassLoader());
            this.rejectedClassName = rejectedClassName;
        }

        MethodHandle loadFormattingHandle() throws Throwable {
            Class<?> actionClass = Class.forName(BootstrapFormattingInvoker.class.getName(), true, this);
            return MethodHandles.publicLookup().findStatic(
                    actionClass,
                    "format",
                    MethodType.methodType(String.class, Throwable.class)
            );
        }

        int rejectedCount() {
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
            ClassLoader testClassLoader = FormattersAnonymous12Test.class.getClassLoader();
            try (InputStream inputStream = testClassLoader.getResourceAsStream(resourceName)) {
                byte[] classBytes = Objects.requireNonNull(inputStream, resourceName).readAllBytes();
                return defineClass(name, classBytes, 0, classBytes.length);
            } catch (IOException e) {
                throw new ClassNotFoundException(name, e);
            }
        }

        private boolean shouldDefineLocally(final String name) {
            return name.startsWith("org.jboss.logmanager.")
                    || FormattersAnonymous12Test.class.getName().equals(name)
                    || BootstrapFormattingInvoker.class.getName().equals(name);
        }
    }
}

final class FormattersAnonymous12DefinedClass {
}
