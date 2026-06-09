/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javassist.javassist;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

import javassist.bytecode.AccessFlag;
import javassist.bytecode.ClassFile;
import javassist.util.proxy.FactoryHelper;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class FactoryHelperTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void mapsPrimitiveTypesToProxyHelperMetadata() {
        int integerIndex = FactoryHelper.typeIndex(int.class);
        int doubleIndex = FactoryHelper.typeIndex(double.class);
        int voidIndex = FactoryHelper.typeIndex(void.class);

        assertThat(integerIndex).isEqualTo(4);
        assertThat(doubleIndex).isEqualTo(7);
        assertThat(voidIndex).isEqualTo(8);
        assertThat(FactoryHelper.primitiveTypes[integerIndex]).isSameAs(int.class);
        assertThat(FactoryHelper.wrapperTypes[integerIndex]).isEqualTo(Integer.class.getName());
        assertThat(FactoryHelper.wrapperDesc[integerIndex]).isEqualTo("(I)V");
        assertThat(FactoryHelper.unwarpMethods[integerIndex]).isEqualTo("intValue");
        assertThat(FactoryHelper.unwrapDesc[integerIndex]).isEqualTo("()I");
        assertThat(FactoryHelper.dataSize[doubleIndex]).isEqualTo(2);
    }

    @Test
    void initializesFactoryHelperFromChildClassLoaderClient() throws Exception {
        URL testCodeSource = FactoryHelperTest.class.getProtectionDomain()
                .getCodeSource()
                .getLocation();
        URL libraryCodeSource = FactoryHelper.class.getProtectionDomain()
                .getCodeSource()
                .getLocation();

        try (ChildFirstJavassistLoader loader = new ChildFirstJavassistLoader(
                testCodeSource, libraryCodeSource)) {
            Class<?> initializer = Class.forName(
                    IsolatedFactoryHelperInitializer.class.getName(), true, loader);

            assertThat(initializer.getClassLoader()).isIn((Object[]) expectedLoaders(loader));
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    @Test
    void writesClassFileIntoPackageDirectory() throws Exception {
        ClassFile classFile = new ClassFile(
                false, "example.FactoryHelperGeneratedFixture", Object.class.getName());
        classFile.setAccessFlags(AccessFlag.PUBLIC);

        FactoryHelper.writeFile(classFile, temporaryDirectory.toString());

        Path generatedClassFile = temporaryDirectory.resolve(
                "example/FactoryHelperGeneratedFixture.class");
        assertThat(generatedClassFile).isRegularFile();
        assertThat(Files.readAllBytes(generatedClassFile))
                .startsWith(new byte[] {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE});
    }

    public static final class IsolatedFactoryHelperInitializer {
        static final int INTEGER_INDEX = FactoryHelper.typeIndex(int.class);

        private IsolatedFactoryHelperInitializer() {
        }
    }

    private static ClassLoader[] expectedLoaders(ClassLoader loader) {
        if (isNativeImageRuntime()) {
            return new ClassLoader[] { loader, ClassLoader.getSystemClassLoader() };
        }
        return new ClassLoader[] { loader };
    }

    private static boolean isNativeImageRuntime() {
        return "runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"));
    }

    private static final class ChildFirstJavassistLoader extends URLClassLoader {
        private ChildFirstJavassistLoader(URL testCodeSource, URL libraryCodeSource) {
            super(new URL[] { testCodeSource, libraryCodeSource }, null);
        }

        @Override
        protected synchronized Class<?> loadClass(String name, boolean resolve)
                throws ClassNotFoundException {
            if (!name.startsWith("javassist.")) {
                return super.loadClass(name, resolve);
            }

            Class<?> loadedClass = findLoadedClass(name);
            if (loadedClass == null) {
                try {
                    loadedClass = findClass(name);
                } catch (ClassNotFoundException exception) {
                    loadedClass = super.loadClass(name, false);
                }
            }
            if (resolve) {
                resolveClass(loadedClass);
            }
            return loadedClass;
        }
    }
}
