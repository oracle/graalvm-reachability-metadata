/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package h2;

import org.graalvm.internal.tck.NativeImageSupport;
import org.h2.util.MemoryUnmapper;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ServiceLoader;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

public class MemoryUnmapperTest {
    @Test
    void unmapRejectsHeapBuffersWithoutThrowing() {
        ByteBuffer buffer = ByteBuffer.allocate(16);

        boolean unmapped = MemoryUnmapper.unmap(buffer);

        assertThat(unmapped).isFalse();
        assertThat(buffer.capacity()).isEqualTo(16);
    }

    @Test
    void unmapAttemptsToCleanDirectBuffers() {
        ByteBuffer buffer = ByteBuffer.allocateDirect(16);
        buffer.putInt(42);

        assertThatCode(() -> MemoryUnmapper.unmap(buffer)).doesNotThrowAnyException();
    }

    @Test
    void unmapUsesLegacyCleanerFallbackWhenUnsafeIsUnavailable() throws Exception {
        assertDynamicClassLoading(() -> {
            Path servicesDirectory = createServiceDescriptor();
            URL testClasses = MemoryUnmapperTest.class.getProtectionDomain().getCodeSource().getLocation();
            URL h2Jar = MemoryUnmapper.class.getProtectionDomain().getCodeSource().getLocation();

            try (LegacyFallbackClassLoader classLoader = new LegacyFallbackClassLoader(
                    new URL[] { servicesDirectory.toUri().toURL(), testClasses, h2Jar })) {
                ServiceLoader<BooleanSupplier> serviceLoader = ServiceLoader.load(BooleanSupplier.class, classLoader);
                BooleanSupplier exercise = serviceLoader.findFirst().orElseThrow();

                boolean unmapped = exercise.getAsBoolean();

                assertThat(unmapped).isTrue();
            }
        });
    }

    private static Path createServiceDescriptor() throws Exception {
        Path servicesDirectory = Files.createTempDirectory("h2-memory-unmapper-services");
        Path serviceDescriptor = servicesDirectory.resolve("META-INF/services/java.util.function.BooleanSupplier");
        Files.createDirectories(serviceDescriptor.getParent());
        Files.writeString(serviceDescriptor, LegacyFallbackExercise.class.getName() + '\n');
        return servicesDirectory;
    }

    private static void assertDynamicClassLoading(DynamicClassLoadingAssertion assertion) throws Exception {
        try {
            assertion.run();
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private interface DynamicClassLoadingAssertion {
        void run() throws Exception;
    }

    public static final class LegacyFallbackExercise implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return MemoryUnmapper.unmap(ByteBuffer.allocateDirect(16));
        }
    }

    private static final class LegacyFallbackClassLoader extends URLClassLoader {
        private LegacyFallbackClassLoader(URL[] urls) {
            super(urls, MemoryUnmapperTest.class.getClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.equals("sun.misc.Unsafe")) {
                throw new ClassNotFoundException(name);
            }
            if (!shouldLoadLocally(name)) {
                return super.loadClass(name, resolve);
            }
            synchronized (getClassLoadingLock(name)) {
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

        private static boolean shouldLoadLocally(String name) {
            return name.equals(LegacyFallbackExercise.class.getName())
                    || name.equals("org.h2.util.MemoryUnmapper");
        }
    }
}
