/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjweaver;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

import org.aspectj.apache.bcel.classfile.JavaClass;
import org.aspectj.apache.bcel.util.NonCachingClassLoaderRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class NonCachingClassLoaderRepositoryTest {
    private static final String CLASS_NAME = "org_aspectj.aspectjweaver.RepositoryLoadedType";
    private static final String INTERNAL_CLASS_NAME = "org_aspectj/aspectjweaver/RepositoryLoadedType";
    private static final String CLASS_RESOURCE_NAME = INTERNAL_CLASS_NAME + ".class";

    @Test
    void loadsClassFromClassLoaderResourceStream() throws Exception {
        ClassLoader classLoader = new ByteArrayResourceClassLoader(CLASS_RESOURCE_NAME, minimalClassBytes());
        NonCachingClassLoaderRepository repository = new NonCachingClassLoaderRepository(classLoader);

        JavaClass loadedClass = repository.loadClass(CLASS_NAME);

        assertThat(loadedClass.getClassName()).isEqualTo(CLASS_NAME);
        assertThat(loadedClass.getSuperclassName()).isEqualTo(Object.class.getName());
        assertThat(repository.findClass(CLASS_NAME)).isSameAs(loadedClass);
    }

    private static byte[] minimalClassBytes() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);

        output.writeInt(0xCAFEBABE);
        output.writeShort(0);
        output.writeShort(52);
        output.writeShort(5);
        writeClassConstant(output, 2);
        writeUtf8Constant(output, INTERNAL_CLASS_NAME);
        writeClassConstant(output, 4);
        writeUtf8Constant(output, "java/lang/Object");
        output.writeShort(0x0021);
        output.writeShort(1);
        output.writeShort(3);
        output.writeShort(0);
        output.writeShort(0);
        output.writeShort(0);
        output.writeShort(0);
        output.flush();

        return bytes.toByteArray();
    }

    private static void writeClassConstant(DataOutputStream output, int nameIndex) throws IOException {
        output.writeByte(7);
        output.writeShort(nameIndex);
    }

    private static void writeUtf8Constant(DataOutputStream output, String value) throws IOException {
        output.writeByte(1);
        output.writeUTF(value);
    }

    private static final class ByteArrayResourceClassLoader extends ClassLoader {
        private final String resourceName;
        private final byte[] resourceBytes;

        private ByteArrayResourceClassLoader(String resourceName, byte[] resourceBytes) {
            super(null);
            this.resourceName = resourceName;
            this.resourceBytes = resourceBytes.clone();
        }

        @Override
        public URL getResource(String name) {
            if (!resourceName.equals(name)) {
                return null;
            }
            try {
                return new URL(null, "memory:///" + name, new ByteArrayUrlStreamHandler(resourceBytes));
            } catch (MalformedURLException exception) {
                throw new IllegalStateException("Unable to create in-memory class resource URL", exception);
            }
        }
    }

    private static final class ByteArrayUrlStreamHandler extends URLStreamHandler {
        private final byte[] resourceBytes;

        private ByteArrayUrlStreamHandler(byte[] resourceBytes) {
            this.resourceBytes = resourceBytes.clone();
        }

        @Override
        protected URLConnection openConnection(URL url) {
            return new URLConnection(url) {
                @Override
                public void connect() {
                    connected = true;
                }

                @Override
                public InputStream getInputStream() {
                    return new ByteArrayInputStream(resourceBytes);
                }
            };
        }
    }
}
