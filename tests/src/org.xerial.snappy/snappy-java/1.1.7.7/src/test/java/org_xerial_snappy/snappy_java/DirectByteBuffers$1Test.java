/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_xerial_snappy.snappy_java;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.xerial.snappy.SnappyFramedInputStream;
import org.xerial.snappy.SnappyFramedOutputStream;
import org.xerial.snappy.pool.BufferPool;
import org.xerial.snappy.pool.QuiescentBufferPool;

import static org.assertj.core.api.Assertions.assertThat;

public class DirectByteBuffers$1Test {

    @Test
    void framedRoundTripWithQuiescentPoolReleasesDirectBuffers() throws IOException {
        byte[] input = createPayload(200_000);
        BufferPool bufferPool = QuiescentBufferPool.getInstance();
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();

        try (SnappyFramedOutputStream output = new SnappyFramedOutputStream(compressed, bufferPool)) {
            long written = output.transferFrom(new ByteArrayInputStream(input));

            assertThat(written).isEqualTo(input.length);
        }

        ByteArrayOutputStream restored = new ByteArrayOutputStream();

        try (SnappyFramedInputStream inputStream = new SnappyFramedInputStream(new ByteArrayInputStream(compressed.toByteArray()), bufferPool)) {
            long restoredBytes = inputStream.transferTo(restored);

            assertThat(restoredBytes).isEqualTo(input.length);
        }

        assertThat(restored.toByteArray()).containsExactly(input);
    }

    @Test
    void isolatedClassLoaderTriggersDirectBufferCleanerFallbackPath() throws Exception {
        Path stubClasses = compileUnsafeStub();
        URL testClasses = DirectByteBuffers$1Test.class.getProtectionDomain().getCodeSource().getLocation();
        URL libraryClasses = QuiescentBufferPool.class.getProtectionDomain().getCodeSource().getLocation();

        try (ChildFirstClassLoader classLoader = new ChildFirstClassLoader(
                new URL[]{stubClasses.toUri().toURL(), testClasses, libraryClasses},
                DirectByteBuffers$1Test.class.getClassLoader())) {
            Class<?> unsafeClass = classLoader.loadClass("sun.misc.Unsafe");
            Class<?> childPoolClass = classLoader.loadClass("org.xerial.snappy.pool.QuiescentBufferPool");
            Class<?> actionClass = classLoader.loadClass(DirectByteBuffersFallbackAction.class.getName());
            Runnable action = actionClass
                    .asSubclass(Runnable.class)
                    .getDeclaredConstructor()
                    .newInstance();

            action.run();

            assertThat(unsafeClass.getClassLoader()).isSameAs(classLoader);
            assertThat(childPoolClass.getClassLoader()).isSameAs(classLoader);
            assertThat(actionClass.getClassLoader()).isSameAs(classLoader);
            assertThat(unsafeClass.getProtectionDomain().getCodeSource().getLocation())
                    .isEqualTo(stubClasses.toUri().toURL());
        }
    }

    private static Path compileUnsafeStub() throws IOException {
        Path classRoot = Files.createTempDirectory("snappy-unsafe-stub-");
        Path classFile = classRoot.resolve(Path.of("sun", "misc", "Unsafe.class"));

        Files.createDirectories(classFile.getParent());
        Files.write(classFile, Base64.getDecoder().decode(UNSAFE_STUB_CLASS));

        return classRoot;
    }

    private static byte[] createPayload(int size) {
        byte[] payload = new byte[size];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) ('A' + (i % 23));
        }
        return payload;
    }

    private static final String UNSAFE_STUB_CLASS =
            "yv66vgAAADQAEgoAAgADBwAEDAAFAAYBABBqYXZhL2xhbmcvT2JqZWN0AQAGPGluaXQ+AQADKClWCQAIAAkHAAoMAAsADAEAD3N1bi9taXNjL1Vuc2FmZQEACXRoZVVuc2FmZQEAEUxzdW4vbWlzYy9VbnNhZmU7AQAEQ29kZQEAD0xpbmVOdW1iZXJUYWJsZQEACDxjbGluaXQ+AQAKU291cmNlRmlsZQEAC1Vuc2FmZS5qYXZhADEACAACAAAAAQAZAAsADAAAAAIAAQAFAAYAAQANAAAAHQABAAEAAAAFKrcAAbEAAAABAA4AAAAGAAEAAAADAAgADwAGAAEADQAAAB0AAQAAAAAABQGzAAexAAAAAQAOAAAABgABAAAABAABABAAAAACABE=";

    public static final class DirectByteBuffersFallbackAction implements Runnable {

        @Override
        public void run() {
            BufferPool bufferPool = QuiescentBufferPool.getInstance();
            ByteBuffer buffer = bufferPool.allocateDirect(64);

            buffer.put((byte) 1);
            buffer.flip();
            bufferPool.releaseDirect(buffer);
        }
    }

    private static final class ChildFirstClassLoader extends URLClassLoader {
        private final List<String> childFirstNames = List.of(
                "sun.misc.Unsafe",
                "org.xerial.snappy.",
                DirectByteBuffersFallbackAction.class.getName()
        );

        private ChildFirstClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                if (shouldLoadChildFirst(name)) {
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
                return super.loadClass(name, resolve);
            }
        }

        private boolean shouldLoadChildFirst(String name) {
            for (String childFirstName : childFirstNames) {
                if (name.equals(childFirstName) || name.startsWith(childFirstName)) {
                    return true;
                }
            }
            return false;
        }
    }
}
