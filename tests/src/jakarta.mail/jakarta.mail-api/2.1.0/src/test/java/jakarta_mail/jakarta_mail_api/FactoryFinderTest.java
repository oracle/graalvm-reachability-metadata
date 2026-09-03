/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.glassfish.hk2.osgiresourcelocator;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;

import jakarta.mail.util.LineInputStream;
import jakarta.mail.util.LineOutputStream;
import jakarta.mail.util.StreamProvider;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FactoryFinderTest {

    private static final String STREAM_PROVIDER_FACTORY_ID = StreamProvider.class.getName();

    @Test
    void createsSystemPropertyProviderUsingContextClassLoader() throws Exception {
        withSystemProperty(STREAM_PROVIDER_FACTORY_ID, TestStreamProvider.class.getName(), () -> {
            StreamProvider provider = withContextClassLoader(
                    FactoryFinderTest.class.getClassLoader(),
                    StreamProvider::provider);

            assertThat(provider).isInstanceOf(TestStreamProvider.class);
        });
    }

    @Test
    void createsSystemPropertyProviderUsingDefaultClassLoaderWhenContextClassLoaderIsNull() throws Exception {
        withSystemProperty(STREAM_PROVIDER_FACTORY_ID, TestStreamProvider.class.getName(), () -> {
            StreamProvider provider = withContextClassLoader(null, StreamProvider::provider);

            assertThat(provider).isInstanceOf(TestStreamProvider.class);
        });
    }

    @Test
    void usesOsgiLookupWhenStandardServiceLoaderHasNoProvider() throws Exception {
        assertThat(ServiceLoader.lookupProviderInstances(StreamProvider.class)).isNull();
        TestStreamProvider osgiProvider = new TestStreamProvider();

        withTestOsgiServiceLoader(osgiProvider, () -> {
            withClearedSystemProperty(STREAM_PROVIDER_FACTORY_ID, () -> {
                ClassLoader classLoader = classLoaderWithoutServiceConfigurationResources();
                StreamProvider provider = withContextClassLoader(classLoader, StreamProvider::provider);

                assertThat(provider).isSameAs(osgiProvider);
            });
        });
    }

    private static void withTestOsgiServiceLoader(StreamProvider provider, ThrowingRunnable runnable) throws Exception {
        ServiceLoader.initialize(new TestOsgiServiceLoader(provider));
        try {
            runnable.run();
        } finally {
            ServiceLoader.reset();
        }
    }

    private static ClassLoader classLoaderWithoutServiceConfigurationResources() {
        return new ClassLoader(null) {
            @Override
            public Enumeration<URL> getResources(String name) {
                return Collections.emptyEnumeration();
            }
        };
    }

    private static void withSystemProperty(String property, String value, ThrowingRunnable runnable) throws Exception {
        String originalValue = System.getProperty(property);
        boolean hadOriginalValue = System.getProperties().containsKey(property);
        System.setProperty(property, value);
        try {
            runnable.run();
        } finally {
            restoreSystemProperty(property, originalValue, hadOriginalValue);
        }
    }

    private static void withClearedSystemProperty(String property, ThrowingRunnable runnable) throws Exception {
        String originalValue = System.getProperty(property);
        boolean hadOriginalValue = System.getProperties().containsKey(property);
        System.clearProperty(property);
        try {
            runnable.run();
        } finally {
            restoreSystemProperty(property, originalValue, hadOriginalValue);
        }
    }

    private static void restoreSystemProperty(String property, String originalValue, boolean hadOriginalValue) {
        if (hadOriginalValue) {
            System.setProperty(property, originalValue);
        } else {
            System.clearProperty(property);
        }
    }

    private static <T> T withContextClassLoader(
            ClassLoader classLoader,
            ThrowingSupplier<T> supplier) throws Exception {
        Thread currentThread = Thread.currentThread();
        ClassLoader originalClassLoader = currentThread.getContextClassLoader();
        currentThread.setContextClassLoader(classLoader);
        try {
            return supplier.get();
        } finally {
            currentThread.setContextClassLoader(originalClassLoader);
        }
    }

    private static final class TestOsgiServiceLoader extends ServiceLoader {

        private final StreamProvider provider;

        private TestOsgiServiceLoader(StreamProvider provider) {
            this.provider = provider;
        }

        @Override
        <T> Iterable<? extends T> lookupProviderInstances1(Class<T> serviceType, ProviderFactory<T> factory) {
            if (serviceType.isInstance(provider)) {
                return Collections.singleton(serviceType.cast(provider));
            }
            return Collections.emptyList();
        }

        @Override
        <T> Iterable<Class> lookupProviderClasses1(Class<T> serviceType) {
            return Collections.emptyList();
        }
    }

    public static final class TestStreamProvider implements StreamProvider {

        public TestStreamProvider() {
        }

        @Override
        public InputStream inputBase64(InputStream in) {
            return in;
        }

        @Override
        public OutputStream outputBase64(OutputStream out) {
            return out;
        }

        @Override
        public InputStream inputBinary(InputStream in) {
            return in;
        }

        @Override
        public OutputStream outputBinary(OutputStream out) {
            return out;
        }

        @Override
        public OutputStream outputB(OutputStream out) {
            return out;
        }

        @Override
        public InputStream inputQ(InputStream in) {
            return in;
        }

        @Override
        public OutputStream outputQ(OutputStream out, boolean encodingWord) {
            return out;
        }

        @Override
        public LineInputStream inputLineStream(InputStream in, boolean allowUtf8) {
            return () -> null;
        }

        @Override
        public LineOutputStream outputLineStream(OutputStream out, boolean allowUtf8) {
            return new TestLineOutputStream(out);
        }

        @Override
        public InputStream inputQP(InputStream in) {
            return in;
        }

        @Override
        public OutputStream outputQP(OutputStream out) {
            return out;
        }

        @Override
        public InputStream inputSharedByteArray(byte[] buff) {
            return new ByteArrayInputStream(buff);
        }

        @Override
        public InputStream inputUU(InputStream in) {
            return in;
        }

        @Override
        public OutputStream outputUU(OutputStream out, String filename) {
            return out;
        }
    }

    private static final class TestLineOutputStream implements LineOutputStream {

        private final OutputStream out;

        private TestLineOutputStream(OutputStream out) {
            this.out = out;
        }

        @Override
        public void writeln(String s) throws IOException {
            out.write(s.getBytes(StandardCharsets.US_ASCII));
            writeln();
        }

        @Override
        public void writeln() throws IOException {
            out.write('\r');
            out.write('\n');
        }

        @Override
        public void write(byte[] content) throws IOException {
            out.write(content);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
