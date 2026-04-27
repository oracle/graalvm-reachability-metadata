/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_iq80_snappy.snappy;

import org.iq80.snappy.Snappy;
import org.iq80.snappy.SnappyInputStream;
import org.iq80.snappy.SnappyOutputStream;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

public class SnappyInternalUtilsTest {
    private static final String UNSAFE_MEMORY_CLASS_NAME = "org.iq80.snappy.UnsafeMemory";

    @Test
    void compressesAndDecompressesBlockPayload() throws Exception {
        byte[] payload = createCompressiblePayload();

        byte[] compressed = Snappy.compress(payload);
        byte[] decompressed = Snappy.uncompress(compressed, 0, compressed.length);

        assertThat(compressed).isNotEmpty();
        assertThat(Snappy.getUncompressedLength(compressed, 0)).isEqualTo(payload.length);
        assertThat(decompressed).isEqualTo(payload);
    }

    @Test
    void fallsBackToSlowMemoryWhenUnsafeMemoryCannotBeLoaded() throws Exception {
        byte[] payload = createCompressiblePayload();
        IsolatingSnappyClassLoader classLoader = new IsolatingSnappyClassLoader(UNSAFE_MEMORY_CLASS_NAME);
        SnappyRoundTrip snappyRoundTrip = createIsolatedRoundTrip(classLoader);

        byte[] decompressed = snappyRoundTrip.roundTrip(payload);

        assertThat(decompressed).isEqualTo(payload);
        assertThat(classLoader.wasRejected()).isTrue();
    }

    @SuppressWarnings("deprecation")
    @Test
    void writesAndReadsSnappyStreamPayload() throws Exception {
        byte[] payload = createCompressiblePayload();
        ByteArrayOutputStream compressedOutput = new ByteArrayOutputStream();

        try (SnappyOutputStream snappyOutput = new SnappyOutputStream(compressedOutput)) {
            snappyOutput.write(payload, 0, 256);
            snappyOutput.write(payload, 256, payload.length - 256);
        }

        byte[] compressed = compressedOutput.toByteArray();
        byte[] decompressed = readSnappyStream(compressed);

        assertThat(compressed).isNotEmpty();
        assertThat(decompressed).isEqualTo(payload);
    }

    private static SnappyRoundTrip createIsolatedRoundTrip(IsolatingSnappyClassLoader classLoader) throws Exception {
        try {
            return classLoader.loadClass(IsolatedSnappyRoundTrip.class.getName())
                    .asSubclass(SnappyRoundTrip.class)
                    .getDeclaredConstructor()
                    .newInstance();
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw e;
        }
    }

    private static byte[] readSnappyStream(byte[] compressed) throws IOException {
        try (SnappyInputStream snappyInput = new SnappyInputStream(new ByteArrayInputStream(compressed))) {
            return snappyInput.readAllBytes();
        }
    }

    private static byte[] createCompressiblePayload() {
        byte[] phrase = "snappy dynamic access coverage payload ".getBytes(StandardCharsets.UTF_8);
        byte[] payload = new byte[phrase.length * 128];

        for (int offset = 0; offset < payload.length; offset += phrase.length) {
            System.arraycopy(phrase, 0, payload, offset, phrase.length);
        }
        Arrays.fill(payload, 64, 128, (byte) 'x');
        return payload;
    }

    public interface SnappyRoundTrip {
        byte[] roundTrip(byte[] payload) throws Exception;
    }

    public static final class IsolatedSnappyRoundTrip implements SnappyRoundTrip {
        @Override
        public byte[] roundTrip(byte[] payload) throws Exception {
            byte[] compressed = Snappy.compress(payload);
            return Snappy.uncompress(compressed, 0, compressed.length);
        }
    }

    private static final class IsolatingSnappyClassLoader extends ClassLoader {
        private final String rejectedClassName;
        private boolean rejected;

        private IsolatingSnappyClassLoader(String rejectedClassName) {
            super(SnappyInternalUtilsTest.class.getClassLoader());
            this.rejectedClassName = rejectedClassName;
        }

        boolean wasRejected() {
            return rejected;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> alreadyLoaded = findLoadedClass(name);
                if (alreadyLoaded != null) {
                    return alreadyLoaded;
                }
                if (rejectedClassName.equals(name)) {
                    rejected = true;
                    throw new ClassNotFoundException(name);
                }
                if (shouldDefineLocally(name)) {
                    Class<?> localClass = defineLocalClass(name);
                    if (resolve) {
                        resolveClass(localClass);
                    }
                    return localClass;
                }
                return super.loadClass(name, resolve);
            }
        }

        private boolean shouldDefineLocally(String name) {
            return name.startsWith("org.iq80.snappy.") || name.equals(IsolatedSnappyRoundTrip.class.getName());
        }

        private Class<?> defineLocalClass(String name) throws ClassNotFoundException {
            String resourceName = name.replace('.', '/') + ".class";
            try (InputStream inputStream = getParent().getResourceAsStream(resourceName)) {
                byte[] classBytes = Objects.requireNonNull(inputStream, resourceName).readAllBytes();
                return defineClass(name, classBytes, 0, classBytes.length);
            } catch (IOException e) {
                throw new ClassNotFoundException(name, e);
            }
        }
    }
}
