/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.taskdefs.compilers.CompilerAdapter;
import org.apache.tools.ant.taskdefs.compilers.CompilerAdapterFactory;
import org.apache.tools.ant.taskdefs.compilers.Jikes;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class CompilerAdapterFactoryTest {
    private static final String MODERN_COMPILER_CLASS_NAME = "com.sun.tools.javac.Main";

    @Test
    void resolvesCompilerAdapterFromFullyQualifiedClassName() {
        try {
            CompilerAdapter adapter = CompilerAdapterFactory.getCompiler(
                    Jikes.class.getName(),
                    new LoggingTask());

            assertThat(adapter).isInstanceOf(Jikes.class);
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    @Test
    void modernCompilerProbeUsesFactoryClassLoaderWhenSystemLookupFails() throws Exception {
        try {
            invokeModernCompilerLookupWithoutJavacMain();
            fail("Expected Ant to report that the modern compiler is unavailable");
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();

            assertThat(cause)
                    .isInstanceOf(BuildException.class)
                    .hasMessageContaining("Unable to find a javac compiler")
                    .hasMessageContaining(MODERN_COMPILER_CLASS_NAME);
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static void invokeModernCompilerLookupWithoutJavacMain() throws Exception {
        ClassLoader parent = CompilerAdapterFactoryTest.class.getClassLoader();
        ClassLoader classLoader = new ModernCompilerHidingClassLoader(parent);
        Class<?> factoryClass = classLoader.loadClass(CompilerAdapterFactory.class.getName());
        Method getCompiler = factoryClass.getMethod("getCompiler", String.class, Task.class);

        getCompiler.invoke(null, "modern", null);
    }

    private static final class LoggingTask extends Task {
        @Override
        public void execute() {
        }
    }

    private static final class ModernCompilerHidingClassLoader extends ClassLoader {
        private static final String FACTORY_CLASS_NAME = CompilerAdapterFactory.class.getName();

        ModernCompilerHidingClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (MODERN_COMPILER_CLASS_NAME.equals(name)) {
                throw new ClassNotFoundException(name);
            }
            if (FACTORY_CLASS_NAME.equals(name)) {
                synchronized (getClassLoadingLock(name)) {
                    Class<?> loadedClass = findLoadedClass(name);
                    if (loadedClass == null) {
                        loadedClass = findClass(name);
                    }
                    if (resolve) {
                        resolveClass(loadedClass);
                    }
                    return loadedClass;
                }
            }
            return super.loadClass(name, resolve);
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (!FACTORY_CLASS_NAME.equals(name)) {
                return super.findClass(name);
            }
            byte[] bytes = readClassBytes(name);
            return defineClass(name, bytes, 0, bytes.length);
        }

        private byte[] readClassBytes(String className) throws ClassNotFoundException {
            String resourceName = className.replace('.', '/') + ".class";
            InputStream resource = getParent().getResourceAsStream(resourceName);
            if (resource == null) {
                throw new ClassNotFoundException(className);
            }
            try (InputStream input = resource) {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int read = input.read(buffer);
                while (read != -1) {
                    output.write(buffer, 0, read);
                    read = input.read(buffer);
                }
                return output.toByteArray();
            } catch (IOException exception) {
                throw new ClassNotFoundException(className, exception);
            }
        }
    }
}
