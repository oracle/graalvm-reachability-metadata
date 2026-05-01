/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_reload4j.reload4j;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.log4j.EnhancedThrowableRenderer;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class EnhancedThrowableRendererTest {
    private static final String TEST_CLASS_NAME = EnhancedThrowableRendererTest.class.getName();
    private static final String MISSING_CLASS_NAME = "missing.reload4j.UnavailableStackFrame";

    @Test
    void rendersStackTraceElementsResolvedByThreadContextClassLoader() {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(
                new ReturningLoadClassLoader(TEST_CLASS_NAME, EnhancedThrowableRendererTest.class));
        try {
            Throwable throwable = throwableWithStackFrame(TEST_CLASS_NAME);

            String[] renderedLines = new EnhancedThrowableRenderer().doRender(throwable);

            assertThat(renderedLines[0]).contains("IllegalStateException: enhanced throwable");
            assertThat(renderedLines[1]).startsWith(expectedTestStackLinePrefix()).endsWith("]");
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    void fallsBackToClassForNameWhenThreadContextClassLoaderRejectsStackClass() {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(new RejectingLoadClassLoader(TEST_CLASS_NAME));
        try {
            Throwable throwable = throwableWithStackFrame(TEST_CLASS_NAME);

            String[] renderedLines = new EnhancedThrowableRenderer().doRender(throwable);

            assertThat(renderedLines[1]).startsWith(expectedTestStackLinePrefix()).endsWith("]");
        } catch (Error error) {
            rethrowIfNotNativeImageDynamicClassLoadingError(error);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    void attemptsRendererClassLoaderWhenStackClassIsUnavailable() {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(new RejectingLoadClassLoader(MISSING_CLASS_NAME));
        try {
            Throwable throwable = throwableWithStackFrame(MISSING_CLASS_NAME);

            String[] renderedLines = new EnhancedThrowableRenderer().doRender(throwable);

            assertThat(renderedLines[1]).isEqualTo("\tat " + MISSING_CLASS_NAME
                    + ".renderedMethod(UnavailableStackFrame.java:37)");
        } catch (Error error) {
            rethrowIfNotNativeImageDynamicClassLoadingError(error);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    private static Throwable throwableWithStackFrame(String className) {
        Throwable throwable = new IllegalStateException("enhanced throwable");
        throwable.setStackTrace(new StackTraceElement[] {
                new StackTraceElement(className, "renderedMethod", simpleSourceName(className), 37) });
        return throwable;
    }

    private static String simpleSourceName(String className) {
        return className.substring(className.lastIndexOf('.') + 1) + ".java";
    }

    private static String expectedTestStackLinePrefix() {
        return "\tat " + TEST_CLASS_NAME + ".renderedMethod(EnhancedThrowableRendererTest.java:37)[";
    }

    private static void rethrowIfNotNativeImageDynamicClassLoadingError(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
        }
    }

    private static final class ReturningLoadClassLoader extends ClassLoader {
        private final String className;
        private final Class<?> loadedClass;

        private ReturningLoadClassLoader(String className, Class<?> loadedClass) {
            super(null);
            this.className = className;
            this.loadedClass = loadedClass;
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (className.equals(name)) {
                return loadedClass;
            }
            return super.loadClass(name);
        }
    }

    private static final class RejectingLoadClassLoader extends ClassLoader {
        private final String rejectedClassName;

        private RejectingLoadClassLoader(String rejectedClassName) {
            super(null);
            this.rejectedClassName = rejectedClassName;
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (rejectedClassName.equals(name)) {
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name);
        }
    }
}
