/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package log4j.log4j;

import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;

import org.apache.log4j.EnhancedThrowableRenderer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class EnhancedThrowableRendererTest {

    @Test
    void rendersStackTraceElementsWithResolvedClassDetails() {
        EnhancedThrowableRenderer renderer = new EnhancedThrowableRenderer();
        RuntimeException throwable = new RuntimeException("resolved");
        StackTraceElement frame = new StackTraceElement(ResolvedFrameType.class.getName(), "invoke", "ResolvedFrameType.java", 17);
        throwable.setStackTrace(new StackTraceElement[]{frame});

        String[] rendered = renderer.doRender(throwable);

        assertThat(rendered).hasSize(2);
        assertThat(rendered[0]).isEqualTo(throwable.toString());
        assertThat(rendered[1]).startsWith("\tat " + frame).contains("[").endsWith("]");
    }

    @Test
    void fallsBackToClassForNameWhenContextClassLoaderCannotLoadStackFrameClass() {
        Thread thread = Thread.currentThread();
        ClassLoader previousClassLoader = thread.getContextClassLoader();
        thread.setContextClassLoader(new RejectingClassLoader(previousClassLoader, FallbackFrameType.class.getName()));

        try {
            EnhancedThrowableRenderer renderer = new EnhancedThrowableRenderer();
            RuntimeException throwable = new RuntimeException("fallback");
            StackTraceElement frame = new StackTraceElement(FallbackFrameType.class.getName(), "call", "FallbackFrameType.java", 23);
            throwable.setStackTrace(new StackTraceElement[]{frame});

            String[] rendered = renderer.doRender(throwable);

            assertThat(rendered).hasSize(2);
            assertThat(rendered[0]).isEqualTo(throwable.toString());
            assertThat(rendered[1]).startsWith("\tat " + frame).contains("[").endsWith("]");
        } finally {
            thread.setContextClassLoader(previousClassLoader);
        }
    }

    @Test
    void fallsBackToRendererClassLoaderWhenContextLoaderAndClassForNameFail() throws Exception {
        StackTraceElement frame = new StackTraceElement(IsolatedFrameType.class.getName(), "call", "IsolatedFrameType.java", 31);
        Thread thread = Thread.currentThread();
        ClassLoader previousClassLoader = thread.getContextClassLoader();
        thread.setContextClassLoader(new RejectingClassLoader(previousClassLoader, IsolatedFrameType.class.getName()));

        try (URLClassLoader isolatedLoader = new FailOnceClassLoader(
                new URL[]{codeSourceUrl(EnhancedThrowableRenderer.class), codeSourceUrl(EnhancedThrowableRendererTest.class)},
                ClassLoader.getPlatformClassLoader(),
                IsolatedFrameType.class.getName())) {
            Class<?> rendererClass = Class.forName(EnhancedThrowableRenderer.class.getName(), true, isolatedLoader);
            Object renderer = rendererClass.getConstructor().newInstance();
            RuntimeException throwable = new RuntimeException("isolated");
            throwable.setStackTrace(new StackTraceElement[]{frame});

            String[] rendered = (String[]) rendererClass.getMethod("doRender", Throwable.class).invoke(renderer, throwable);

            assertThat(rendered).hasSize(2);
            assertThat(rendered[0]).isEqualTo(throwable.toString());
            assertThat(rendered[1]).startsWith("\tat " + frame).contains("[").endsWith("]");
        } finally {
            thread.setContextClassLoader(previousClassLoader);
        }
    }

    @Test
    void keepsOriginalStackTraceElementTextWhenClassCannotBeResolved() {
        EnhancedThrowableRenderer renderer = new EnhancedThrowableRenderer();
        RuntimeException throwable = new RuntimeException("missing");
        StackTraceElement frame = new StackTraceElement("missing.example.DoesNotExist", "call", null, -1);
        throwable.setStackTrace(new StackTraceElement[]{frame});

        String[] rendered = renderer.doRender(throwable);

        assertThat(rendered).containsExactly(throwable.toString(), "\tat " + frame);
    }

    private static URL codeSourceUrl(Class<?> type) {
        CodeSource codeSource = type.getProtectionDomain().getCodeSource();
        assertThat(codeSource).isNotNull();
        return codeSource.getLocation();
    }

    public static final class ResolvedFrameType {
    }

    public static final class FallbackFrameType {
    }

    public static final class IsolatedFrameType {
    }

    private static final class RejectingClassLoader extends ClassLoader {
        private final String rejectedClassName;

        private RejectingClassLoader(ClassLoader parent, String rejectedClassName) {
            super(parent);
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

    private static final class FailOnceClassLoader extends URLClassLoader {
        private final String failOnceClassName;
        private boolean failed;

        private FailOnceClassLoader(URL[] urls, ClassLoader parent, String failOnceClassName) {
            super(urls, parent);
            this.failOnceClassName = failOnceClassName;
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (!failed && failOnceClassName.equals(name)) {
                failed = true;
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name);
        }
    }
}
