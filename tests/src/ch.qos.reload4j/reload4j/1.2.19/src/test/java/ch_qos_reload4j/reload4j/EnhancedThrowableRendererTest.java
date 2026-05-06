/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_reload4j.reload4j;

import java.util.ArrayList;

import org.apache.log4j.EnhancedThrowableRenderer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class EnhancedThrowableRendererTest {
    @Test
    void rendersStackTraceElementUsingContextClassLoader() {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        RecordingClassLoader contextClassLoader = new RecordingClassLoader(
                EnhancedThrowableRendererTest.class.getName(),
                EnhancedThrowableRendererTest.class);

        try {
            Thread.currentThread().setContextClassLoader(contextClassLoader);

            String[] rendered = renderThrowableWithStackTraceElement(
                    EnhancedThrowableRendererTest.class.getName(),
                    "rendersStackTraceElementUsingContextClassLoader");

            assertThat(rendered).hasSize(2);
            assertThat(rendered[0]).isEqualTo("java.lang.IllegalStateException: enhanced-renderer-test");
            assertThat(rendered[1]).startsWith("\tat " + EnhancedThrowableRendererTest.class.getName()
                    + ".rendersStackTraceElementUsingContextClassLoader(EnhancedThrowableRendererTest.java:42)");
            assertThat(rendered[1]).contains("[").contains(":").contains("]");
            assertThat(contextClassLoader.loadClassCalls).isEqualTo(1);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    void fallsBackToClassForNameWhenContextClassLoaderCannotLoadElementClass() {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        RecordingClassLoader contextClassLoader = new RecordingClassLoader(null, null);

        try {
            Thread.currentThread().setContextClassLoader(contextClassLoader);

            String[] rendered = renderThrowableWithStackTraceElement(
                    ArrayList.class.getName(),
                    "add");

            assertThat(rendered).hasSize(2);
            assertThat(rendered[1]).startsWith("\tat java.util.ArrayList.add(ArrayList.java:42)");
            assertThat(rendered[1]).contains("[").contains(":").contains("]");
            assertThat(contextClassLoader.loadClassCalls).isEqualTo(1);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    void attemptsRendererClassLoaderWhenClassForNameCannotLoadElementClass() {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        RecordingClassLoader contextClassLoader = new RecordingClassLoader(null, null);

        try {
            Thread.currentThread().setContextClassLoader(contextClassLoader);

            String[] rendered = renderThrowableWithStackTraceElement(
                    "missing.reload4j.DoesNotExist",
                    "missing");

            assertThat(rendered).hasSize(2);
            assertThat(rendered[1]).isEqualTo("\tat missing.reload4j.DoesNotExist.missing(DoesNotExist.java:42)");
            assertThat(contextClassLoader.loadClassCalls).isEqualTo(1);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    private static String[] renderThrowableWithStackTraceElement(String className, String methodName) {
        IllegalStateException throwable = new IllegalStateException("enhanced-renderer-test");
        throwable.setStackTrace(new StackTraceElement[] {
                new StackTraceElement(className, methodName, simpleFileName(className), 42)
        });

        return new EnhancedThrowableRenderer().doRender(throwable);
    }

    private static String simpleFileName(String className) {
        int lastDot = className.lastIndexOf('.');
        return className.substring(lastDot + 1) + ".java";
    }

    private static final class RecordingClassLoader extends ClassLoader {
        private final String loadableName;
        private final Class loadableClass;
        private int loadClassCalls;

        private RecordingClassLoader(String loadableName, Class loadableClass) {
            super(null);
            this.loadableName = loadableName;
            this.loadableClass = loadableClass;
        }

        @Override
        public Class loadClass(String name) throws ClassNotFoundException {
            loadClassCalls++;
            if (name.equals(loadableName)) {
                return loadableClass;
            }
            throw new ClassNotFoundException(name);
        }
    }
}
