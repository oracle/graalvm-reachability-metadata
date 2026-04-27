/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_activation.jakarta_activation_api;

import jakarta.activation.MimeTypeEntry;
import jakarta.activation.MimeTypeRegistry;
import jakarta.activation.MimetypesFileTypeMap;
import jakarta.activation.spi.MimeTypeRegistryProvider;
import org.glassfish.hk2.osgiresourcelocator.ServiceLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class FactoryFinderTest {
    private static final String PROVIDER_PROPERTY = MimeTypeRegistryProvider.class.getName();

    private final Thread currentThread = Thread.currentThread();
    private final ClassLoader originalContextClassLoader = currentThread.getContextClassLoader();
    private final String originalProviderProperty = System.getProperty(PROVIDER_PROPERTY);

    @AfterEach
    void restoreThreadState() {
        currentThread.setContextClassLoader(originalContextClassLoader);
        if (originalProviderProperty == null) {
            System.clearProperty(PROVIDER_PROPERTY);
        } else {
            System.setProperty(PROVIDER_PROPERTY, originalProviderProperty);
        }
        ServiceLoader.reset();
    }

    @Test
    void loadsMimeTypeRegistryProviderFromOsgiServiceLoaderWhenJavaServiceLoaderHasNoProviders() {
        System.clearProperty(PROVIDER_PROPERTY);
        currentThread.setContextClassLoader(new ServiceConfigurationHidingClassLoader(
                originalContextClassLoader,
                MimeTypeRegistryProvider.class
        ));

        MimetypesFileTypeMap typeMap = new MimetypesFileTypeMap(mimeTypes("application/x-osgi osgi"));

        assertThat(typeMap.getContentType("sample.osgi")).isEqualTo("application/x-osgi");
        assertThat(ServiceLoader.getLookedUpServiceTypes()).contains(MimeTypeRegistryProvider.class);
    }

    private static InputStream mimeTypes(String content) {
        return new ByteArrayInputStream((content + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
    }

    public static final class OsgiMimeTypeRegistryProvider implements MimeTypeRegistryProvider {
        public OsgiMimeTypeRegistryProvider() {
        }

        @Override
        public MimeTypeRegistry getByFileName(String name) {
            return new TestMimeTypeRegistry();
        }

        @Override
        public MimeTypeRegistry getByInputStream(InputStream inputStream) throws IOException {
            return TestMimeTypeRegistry.from(inputStream);
        }

        @Override
        public MimeTypeRegistry getInMemory() {
            return new TestMimeTypeRegistry();
        }
    }

    private static final class TestMimeTypeRegistry implements MimeTypeRegistry {
        private final Map<String, String> mimeTypesByExtension = new LinkedHashMap<>();

        private static TestMimeTypeRegistry from(InputStream inputStream) throws IOException {
            TestMimeTypeRegistry registry = new TestMimeTypeRegistry();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8)
            )) {
                String line;
                while ((line = reader.readLine()) != null) {
                    registry.appendToRegistry(line);
                }
            }
            return registry;
        }

        @Override
        public MimeTypeEntry getMimeTypeEntry(String fileExtension) {
            String mimeType = mimeTypesByExtension.get(fileExtension);
            if (mimeType == null) {
                return null;
            }
            return new MimeTypeEntry(mimeType, fileExtension);
        }

        @Override
        public void appendToRegistry(String mimeTypes) {
            String trimmedMimeTypes = mimeTypes.trim();
            if (trimmedMimeTypes.isEmpty() || trimmedMimeTypes.startsWith("#")) {
                return;
            }

            String[] tokens = trimmedMimeTypes.split("\\s+");
            if (tokens.length < 2) {
                return;
            }

            String mimeType = tokens[0];
            for (int index = 1; index < tokens.length; index++) {
                mimeTypesByExtension.put(tokens[index], mimeType);
            }
        }
    }

    private static final class ServiceConfigurationHidingClassLoader extends ClassLoader {
        private final String hiddenServiceConfigurationName;

        private ServiceConfigurationHidingClassLoader(ClassLoader parent, Class<?> hiddenServiceType) {
            super(parent);
            this.hiddenServiceConfigurationName = "META-INF/services/" + hiddenServiceType.getName();
        }

        @Override
        public URL getResource(String name) {
            if (hiddenServiceConfigurationName.equals(name)) {
                return null;
            }
            return super.getResource(name);
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            if (hiddenServiceConfigurationName.equals(name)) {
                return Collections.emptyEnumeration();
            }
            return super.getResources(name);
        }
    }
}
