/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_mail.jakarta_mail_api;

import jakarta.mail.util.LineInputStream;
import jakarta.mail.util.LineOutputStream;
import jakarta.mail.util.StreamProvider;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import org.glassfish.hk2.osgiresourcelocator.ServiceLoader;
import org.glassfish.hk2.osgiresourcelocator.TestOsgiServiceLoader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class FactoryFinderTest {
    private static final String STREAM_PROVIDER_PROPERTY = StreamProvider.class.getName();

    @Test
    void providerInstantiatesSystemPropertyClassWithContextClassLoader() {
        Thread thread = Thread.currentThread();
        ClassLoader originalContextClassLoader = thread.getContextClassLoader();
        String originalProperty = System.getProperty(STREAM_PROVIDER_PROPERTY);

        try {
            thread.setContextClassLoader(FactoryFinderTest.class.getClassLoader());
            System.setProperty(STREAM_PROVIDER_PROPERTY, TestStreamProvider.class.getName());

            StreamProvider provider = StreamProvider.provider();

            assertEquals(TestStreamProvider.class, provider.getClass());
        } finally {
            restoreSystemProperty(originalProperty);
            thread.setContextClassLoader(originalContextClassLoader);
        }
    }

    @Test
    void providerInstantiatesSystemPropertyClassWhenContextClassLoaderIsUnavailable() {
        Thread thread = Thread.currentThread();
        ClassLoader originalContextClassLoader = thread.getContextClassLoader();
        String originalProperty = System.getProperty(STREAM_PROVIDER_PROPERTY);

        try {
            thread.setContextClassLoader(null);
            System.setProperty(STREAM_PROVIDER_PROPERTY, TestStreamProvider.class.getName());

            StreamProvider provider = StreamProvider.provider();

            assertEquals(TestStreamProvider.class, provider.getClass());
        } finally {
            restoreSystemProperty(originalProperty);
            thread.setContextClassLoader(originalContextClassLoader);
        }
    }

    @Test
    void providerChecksOsgiLoaderWhenNoServiceProviderIsAvailable() {
        Thread thread = Thread.currentThread();
        ClassLoader originalContextClassLoader = thread.getContextClassLoader();
        String originalProperty = System.getProperty(STREAM_PROVIDER_PROPERTY);

        boolean osgiServiceLoaderInitialized = false;
        try {
            System.clearProperty(STREAM_PROVIDER_PROPERTY);
            ServiceLoader.initialize(new TestOsgiServiceLoader());
            osgiServiceLoaderInitialized = true;
            thread.setContextClassLoader(new ServiceHidingClassLoader(FactoryFinderTest.class.getClassLoader()));

            IllegalStateException exception = assertThrows(IllegalStateException.class, StreamProvider::provider);

            assertEquals("Not provider of " + StreamProvider.class.getName() + " was found", exception.getMessage());
        } finally {
            if (osgiServiceLoaderInitialized) {
                ServiceLoader.reset();
            }
            restoreSystemProperty(originalProperty);
            thread.setContextClassLoader(originalContextClassLoader);
        }
    }

    private static void restoreSystemProperty(String originalProperty) {
        if (originalProperty == null) {
            System.clearProperty(STREAM_PROVIDER_PROPERTY);
        } else {
            System.setProperty(STREAM_PROVIDER_PROPERTY, originalProperty);
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
        public LineInputStream inputLineStream(InputStream in, boolean allowutf8) {
            return () -> null;
        }

        @Override
        public LineOutputStream outputLineStream(OutputStream out, boolean allowutf8) {
            return new NoOpLineOutputStream();
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

    private static final class ServiceHidingClassLoader extends ClassLoader {
        private static final String STREAM_PROVIDER_SERVICE = "META-INF/services/" + STREAM_PROVIDER_PROPERTY;

        private ServiceHidingClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            if (STREAM_PROVIDER_SERVICE.equals(name)) {
                return Collections.emptyEnumeration();
            }
            return super.getResources(name);
        }
    }

    private static final class NoOpLineOutputStream implements LineOutputStream {
        @Override
        public void writeln(String s) {
        }

        @Override
        public void writeln() {
        }

        @Override
        public void write(byte[] content) {
        }
    }
}
