/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_reload4j.reload4j;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;
import java.util.ServiceLoader;

import org.apache.log4j.EnhancedThrowableRenderer;
import org.junit.jupiter.api.Test;

public class EnhancedThrowableRendererTest {
    private static final String TEST_MESSAGE = "enhanced-renderer-test";
    private static final String ISOLATED_PROVIDER_PACKAGE = "ch_qos_reload4j.reload4j.isolated.";
    private static final String ISOLATED_PROVIDER_CLASS = ISOLATED_PROVIDER_PACKAGE
            + "EnhancedThrowableRendererActionProvider";
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
    void fallsBackToRendererClassLoaderWhenClassForNameCannotLoadElementClass() throws Exception {
        URL[] classLoaderUrls = new URL[] {
                codeSourceUrl(EnhancedThrowableRendererTest.class),
                codeSourceUrl(EnhancedThrowableRenderer.class)
        };
        try (ClassForNameRejectingChildFirstClassLoader classLoader = new ClassForNameRejectingChildFirstClassLoader(
                classLoaderUrls, EnhancedThrowableRendererTest.class.getClassLoader(),
                EnhancedThrowableRendererTest.class.getName())) {
            IsolatedLoaderAction action = findIsolatedRendererAction(classLoader);

            String renderedLine = withContextClassLoader(new RejectingClassLoader(),
                    () -> action.loadClass(EnhancedThrowableRendererTest.class.getName()));

            assertThat(renderedLine)
                    .startsWith("\tat " + EnhancedThrowableRendererTest.class.getName() + ".isolatedRenderedMethod")
                    .contains("[")
                    .contains(":")
                    .endsWith("]");
        }
    }

    @Test
    void attemptsRendererClassLoaderFallbackWhenClassForNameCannotResolveStackElement() throws Exception {
        String[] renderedLines = withContextClassLoader(new RejectingClassLoader(),
                () -> new EnhancedThrowableRenderer().doRender(throwableWithStackTrace(MISSING_CLASS_NAME)));

        assertThat(renderedLines).containsExactly(
                "java.lang.IllegalStateException: " + TEST_MESSAGE,
                "\tat " + MISSING_CLASS_NAME + ".renderedMethod(EnhancedThrowableRendererTest.java:123)");
    }

    private static IsolatedLoaderAction findIsolatedRendererAction(ClassLoader classLoader) {
        for (IsolatedLoaderAction action : ServiceLoader.load(IsolatedLoaderAction.class, classLoader)) {
            if (action.getClass().getName().equals(ISOLATED_PROVIDER_CLASS)) {
                return action;
            }
        }
        throw new AssertionError("Expected an isolated EnhancedThrowableRenderer action provider");
    }

    private static URL codeSourceUrl(Class<?> type) {
        CodeSource codeSource = type.getProtectionDomain().getCodeSource();
        assertThat(codeSource).isNotNull();
        return codeSource.getLocation();
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

    private static final class ClassForNameRejectingChildFirstClassLoader extends URLClassLoader {
        private final String classForNameRejectedClass;

        private ClassForNameRejectingChildFirstClassLoader(URL[] urls, ClassLoader parent,
                String classForNameRejectedClass) {
            super(urls, parent);
            this.classForNameRejectedClass = classForNameRejectedClass;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                if (name.equals(classForNameRejectedClass) && isClassForNameLookup()) {
                    throw new ClassNotFoundException(name);
                }

                Class<?> loadedClass = findLoadedClass(name);
                if (loadedClass == null) {
                    loadedClass = loadUnresolvedClass(name);
                }
                if (resolve) {
                    resolveClass(loadedClass);
                }
                return loadedClass;
            }
        }

        private Class<?> loadUnresolvedClass(String name) throws ClassNotFoundException {
            if (isChildFirst(name)) {
                try {
                    return findClass(name);
                } catch (ClassNotFoundException ignored) {
                    return super.loadClass(name, false);
                }
            }
            return super.loadClass(name, false);
        }

        private boolean isChildFirst(String className) {
            return className.equals(EnhancedThrowableRenderer.class.getName())
                    || className.equals(classForNameRejectedClass)
                    || className.startsWith(ISOLATED_PROVIDER_PACKAGE);
        }

        private boolean isClassForNameLookup() {
            for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
                boolean isClassForNameFrame = element.getClassName().equals(Class.class.getName())
                        && element.getMethodName().startsWith("forName");
                if (isClassForNameFrame) {
                    return true;
                }
            }
            return false;
        }
    }
}
