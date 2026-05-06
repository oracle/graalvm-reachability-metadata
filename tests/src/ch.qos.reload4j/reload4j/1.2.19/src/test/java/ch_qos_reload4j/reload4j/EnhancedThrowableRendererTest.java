/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_reload4j.reload4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import org.apache.log4j.EnhancedThrowableRenderer;
import org.apache.log4j.spi.ThrowableRenderer;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class EnhancedThrowableRendererTest {
    private static final String FALLBACK_ONLY_CLASS_NAME =
            "ch_qos_reload4j.reload4j.EnhancedThrowableRendererFallbackTarget";

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
    void fallsBackToRendererClassLoaderWhenClassForNameCannotLoadElementClass() throws Exception {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        RendererFallbackClassLoader fallbackClassLoader = new RendererFallbackClassLoader(
                EnhancedThrowableRendererTest.class.getClassLoader());

        try {
            Thread.currentThread().setContextClassLoader(fallbackClassLoader);

            ThrowableRenderer renderer = newRendererFrom(fallbackClassLoader);
            String[] rendered = renderThrowableWithStackTraceElement(
                    renderer,
                    FALLBACK_ONLY_CLASS_NAME,
                    "fallback");

            assertThat(rendered).hasSize(2);
            assertThat(rendered[1]).startsWith("\tat " + FALLBACK_ONLY_CLASS_NAME
                    + ".fallback(EnhancedThrowableRendererFallbackTarget.java:42)");
            if (!NativeImageSupport.isInNativeImageRuntime()) {
                assertThat(rendered[1]).contains("[").contains(":").contains("]");
                assertThat(fallbackClassLoader.fallbackClassLoadCalls).isGreaterThanOrEqualTo(3);
            } else {
                assertThat(fallbackClassLoader.fallbackClassLoadCalls).isGreaterThanOrEqualTo(1);
            }
        } catch (ReflectiveOperationException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Error error && NativeImageSupport.isUnsupportedFeatureError(error)) {
                return;
            }
            throw exception;
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    private static ThrowableRenderer newRendererFrom(ClassLoader classLoader) throws ReflectiveOperationException {
        Class<?> rendererClass = classLoader.loadClass(EnhancedThrowableRenderer.class.getName());
        return (ThrowableRenderer) rendererClass.getDeclaredConstructor().newInstance();
    }

    private static String[] renderThrowableWithStackTraceElement(String className, String methodName) {
        return renderThrowableWithStackTraceElement(new EnhancedThrowableRenderer(), className, methodName);
    }

    private static String[] renderThrowableWithStackTraceElement(
            ThrowableRenderer renderer,
            String className,
            String methodName) {
        IllegalStateException throwable = new IllegalStateException("enhanced-renderer-test");
        throwable.setStackTrace(new StackTraceElement[] {
                new StackTraceElement(className, methodName, simpleFileName(className), 42)
        });

        return renderer.doRender(throwable);
    }

    private static String simpleFileName(String className) {
        int lastDot = className.lastIndexOf('.');
        return className.substring(lastDot + 1) + ".java";
    }

    private static final class RendererFallbackClassLoader extends ClassLoader {
        private final ClassLoader parent;
        private int fallbackClassLoadCalls;

        private RendererFallbackClassLoader(ClassLoader parent) {
            super(parent);
            this.parent = parent;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (EnhancedThrowableRenderer.class.getName().equals(name)) {
                Class<?> loadedClass = findLoadedClass(name);
                if (loadedClass == null) {
                    byte[] classBytes = readEnhancedThrowableRendererClassBytes();
                    loadedClass = defineClass(
                            name,
                            classBytes,
                            0,
                            classBytes.length,
                            EnhancedThrowableRenderer.class.getProtectionDomain());
                }
                if (resolve) {
                    resolveClass(loadedClass);
                }
                return loadedClass;
            }
            if (FALLBACK_ONLY_CLASS_NAME.equals(name)) {
                fallbackClassLoadCalls++;
                if (fallbackClassLoadCalls == 1 || isClassForNameLoad()) {
                    throw new ClassNotFoundException(name);
                }
                return EnhancedThrowableRendererTest.class;
            }
            return super.loadClass(name, resolve);
        }

        private boolean isClassForNameLoad() {
            for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
                if (Class.class.getName().equals(element.getClassName())
                        && "forName".equals(element.getMethodName())) {
                    return true;
                }
            }
            return false;
        }

        private byte[] readEnhancedThrowableRendererClassBytes() throws ClassNotFoundException {
            String resourceName = EnhancedThrowableRenderer.class.getName().replace('.', '/') + ".class";
            try (InputStream input = parent.getResourceAsStream(resourceName)) {
                if (input == null) {
                    throw new ClassNotFoundException(EnhancedThrowableRenderer.class.getName());
                }
                return input.readAllBytes();
            } catch (IOException exception) {
                ClassNotFoundException classNotFoundException =
                        new ClassNotFoundException(EnhancedThrowableRenderer.class.getName());
                classNotFoundException.initCause(exception);
                throw classNotFoundException;
            }
        }
    }

    private static final class RecordingClassLoader extends ClassLoader {
        private final String loadableName;
        private final Class<?> loadableClass;
        private int loadClassCalls;

        private RecordingClassLoader(String loadableName, Class<?> loadableClass) {
            super(null);
            this.loadableName = loadableName;
            this.loadableClass = loadableClass;
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            loadClassCalls++;
            if (name.equals(loadableName)) {
                return loadableClass;
            }
            throw new ClassNotFoundException(name);
        }
    }
}
