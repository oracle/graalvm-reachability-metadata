/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mozilla.rhino;

import org.junit.jupiter.api.Test;
import org.mozilla.classfile.ClassFileWriter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassFileWriterTest {
    private static final String CLASS_FILE_WRITER_NAME = "org.mozilla.classfile.ClassFileWriter";
    private static final String CLASS_FILE_WRITER_RESOURCE = "org/mozilla/classfile/ClassFileWriter.class";
    private static final String CLASSFILE_PACKAGE_PREFIX = "org.mozilla.classfile.";

    @Test
    void classFileWriterFallsBackToSystemResourceForItsClassHeader() throws Exception {
        assertSystemResourceAvailable();

        ResourceHidingClassLoader classLoader = new ResourceHidingClassLoader(
                ClassFileWriterTest.class.getClassLoader());
        Class<?> writerClass;
        try {
            writerClass = Class.forName(CLASS_FILE_WRITER_NAME, true, classLoader);
        } catch (Throwable ex) {
            assertThat(isRuntimeClassDefinitionUnavailable(ex)).isTrue();
            ClassFileWriter writer = new ClassFileWriter("org.example.Generated", "java.lang.Object", null);
            assertThat(writer.getClassName()).isEqualTo("org.example.Generated");
            return;
        }

        assertThat(writerClass.getClassLoader()).isSameAs(classLoader);
        assertThat(classLoader.hiddenResourceRequests()).contains(CLASS_FILE_WRITER_RESOURCE);
    }

    private static boolean isRuntimeClassDefinitionUnavailable(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            String className = current.getClass().getName();
            String message = current.getMessage();
            if (className.contains("UnsupportedFeature") || className.contains("UnsupportedOperation")) {
                return true;
            }
            if (message != null
                    && (message.contains("defineClass")
                            || message.contains("dynamic")
                            || message.contains("native image")
                            || message.contains("not supported"))) {
                return true;
            }
        }
        return false;
    }

    private static void assertSystemResourceAvailable() throws IOException {
        try (InputStream in = ClassLoader.getSystemResourceAsStream(CLASS_FILE_WRITER_RESOURCE)) {
            assertThat(in).isNotNull();
        }
    }

    private static byte[] readSystemClassBytes(String resourceName) throws IOException, ClassNotFoundException {
        try (InputStream in = ClassLoader.getSystemResourceAsStream(resourceName)) {
            if (in == null) {
                throw new ClassNotFoundException(resourceName);
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                bytes.write(buffer, 0, read);
            }
            return bytes.toByteArray();
        }
    }

    private static final class ResourceHidingClassLoader extends ClassLoader {
        private final List<String> hiddenResourceRequests = new ArrayList<>();
        private final ProtectionDomain protectionDomain;

        ResourceHidingClassLoader(ClassLoader parent) {
            super(parent);
            this.protectionDomain = ClassFileWriter.class.getProtectionDomain();
        }

        List<String> hiddenResourceRequests() {
            return hiddenResourceRequests;
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            if (CLASS_FILE_WRITER_RESOURCE.equals(name)) {
                hiddenResourceRequests.add(name);
                return null;
            }
            return super.getResourceAsStream(name);
        }

        @Override
        public URL getResource(String name) {
            if (CLASS_FILE_WRITER_RESOURCE.equals(name)) {
                hiddenResourceRequests.add(name);
                return null;
            }
            return super.getResource(name);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.startsWith(CLASSFILE_PACKAGE_PREFIX)) {
                return super.loadClass(name, resolve);
            }

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

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            String resourceName = name.replace('.', '/') + ".class";
            try {
                byte[] classBytes = readSystemClassBytes(resourceName);
                return defineClass(name, classBytes, 0, classBytes.length, protectionDomain);
            } catch (IOException ex) {
                throw new ClassNotFoundException(name, ex);
            }
        }
    }
}
