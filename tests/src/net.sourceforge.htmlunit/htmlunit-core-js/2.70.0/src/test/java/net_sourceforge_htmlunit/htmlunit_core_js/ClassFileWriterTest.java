/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sourceforge_htmlunit.htmlunit_core_js;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.net.URL;
import java.security.ProtectionDomain;

import net.sourceforge.htmlunit.corejs.classfile.ClassFileWriter;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassFileWriterTest {
    private static final String CLASS_NAME =
            "net.sourceforge.htmlunit.corejs.classfile.ClassFileWriter";
    private static final String CLASS_RESOURCE =
            "net/sourceforge/htmlunit/corejs/classfile/ClassFileWriter.class";

    @Test
    void initializesWithSystemResourceFallbackWhenDefiningLoaderHidesClassResource()
            throws Exception {
        try {
            byte[] classBytes = readClassFileWriterBytes();
            ClassLoader hidingLoader = new HidingClassResourceLoader(classBytes);
            Class<?> loadedClass = hidingLoader.loadClass(CLASS_NAME);
            MethodHandles.publicLookup().ensureInitialized(loadedClass);

            assertThat(loadedClass.getName()).isEqualTo(CLASS_NAME);
            assertThat(loadedClass.getClassLoader()).isSameAs(hidingLoader);
            assertThat(((HidingClassResourceLoader) hidingLoader).hiddenResourceRequests)
                    .as("ClassFileWriter should ask its defining loader for its own class resource")
                    .isPositive();
        } catch (Error error) {
            rethrowUnlessUnsupportedFeatureError(error);
        }
    }

    private static byte[] readClassFileWriterBytes() throws IOException {
        try (InputStream input =
                ClassFileWriter.class.getResourceAsStream("ClassFileWriter.class")) {
            assertThat(input).as("ClassFileWriter bytecode resource").isNotNull();

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }

    private static void rethrowUnlessUnsupportedFeatureError(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
        }
    }

    private static final class HidingClassResourceLoader extends ClassLoader {
        private final byte[] classFileWriterBytes;
        private final ProtectionDomain protectionDomain;
        private int hiddenResourceRequests;

        HidingClassResourceLoader(byte[] classFileWriterBytes) {
            super(ClassFileWriterTest.class.getClassLoader());
            this.classFileWriterBytes = classFileWriterBytes;
            this.protectionDomain = ClassFileWriter.class.getProtectionDomain();
        }

        @Override
        public URL getResource(String name) {
            if (CLASS_RESOURCE.equals(name)) {
                hiddenResourceRequests++;
                return null;
            }
            return super.getResource(name);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            if (CLASS_RESOURCE.equals(name)) {
                hiddenResourceRequests++;
                return null;
            }
            return super.getResourceAsStream(name);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!CLASS_NAME.equals(name)) {
                return super.loadClass(name, resolve);
            }

            Class<?> loadedClass = findLoadedClass(name);
            if (loadedClass == null) {
                loadedClass =
                        defineClass(
                                CLASS_NAME,
                                classFileWriterBytes,
                                0,
                                classFileWriterBytes.length,
                                protectionDomain);
            }
            if (resolve) {
                resolveClass(loadedClass);
            }
            return loadedClass;
        }
    }
}
