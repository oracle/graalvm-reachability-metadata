/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_reload4j.reload4j;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.log4j.EnhancedThrowableRenderer;
import org.junit.jupiter.api.Test;

public class EnhancedThrowableRendererTest {
    private static final String TEST_MESSAGE = "enhanced-renderer-test";
    private static final String MISSING_CLASS_NAME = "ch_qos_reload4j.reload4j.MissingEnhancedThrowableRendererClass";

    @Test
    void rendersThrowableStackTraceWithCodeSourceDetails() {
        Throwable throwable = throwableWithStackTrace(EnhancedThrowableRendererTest.class.getName());

        String[] renderedLines = new EnhancedThrowableRenderer().doRender(throwable);

        assertThat(renderedLines).hasSize(2);
        assertThat(renderedLines[0]).isEqualTo("java.lang.IllegalStateException: " + TEST_MESSAGE);
        assertThat(renderedLines[1])
                .startsWith("\tat " + EnhancedThrowableRendererTest.class.getName() + ".renderedMethod")
                .contains("[")
                .contains(":")
                .endsWith("]");
    }

    @Test
    void fallsBackToClassForNameWhenContextClassLoaderCannotLoadElementClass() throws Exception {
        String[] renderedLines = withContextClassLoader(new RejectingClassLoader(),
                () -> new EnhancedThrowableRenderer().doRender(throwableWithStackTrace(String.class.getName())));

        assertThat(renderedLines).hasSize(2);
        assertThat(renderedLines[1])
                .startsWith("\tat " + String.class.getName() + ".renderedMethod")
                .contains(":")
                .endsWith("]");
    }

    @Test
    void attemptsRendererClassLoaderFallbackWhenClassForNameCannotResolveStackElement() throws Exception {
        String[] renderedLines = withContextClassLoader(new RejectingClassLoader(),
                () -> new EnhancedThrowableRenderer().doRender(throwableWithStackTrace(MISSING_CLASS_NAME)));

        assertThat(renderedLines).containsExactly(
                "java.lang.IllegalStateException: " + TEST_MESSAGE,
                "\tat " + MISSING_CLASS_NAME + ".renderedMethod(EnhancedThrowableRendererTest.java:123)");
    }

    private static Throwable throwableWithStackTrace(String className) {
        Throwable throwable = new IllegalStateException(TEST_MESSAGE);
        throwable.setStackTrace(new StackTraceElement[] {
                new StackTraceElement(className, "renderedMethod", "EnhancedThrowableRendererTest.java", 123)
        });
        return throwable;
    }

    private static <T> T withContextClassLoader(ClassLoader classLoader, ThrowingSupplier<T> action) throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader previousClassLoader = thread.getContextClassLoader();
        thread.setContextClassLoader(classLoader);
        try {
            return action.get();
        } finally {
            thread.setContextClassLoader(previousClassLoader);
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    private static final class RejectingClassLoader extends ClassLoader {
        private RejectingClassLoader() {
            super(null);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            throw new ClassNotFoundException(name);
        }
    }
}
