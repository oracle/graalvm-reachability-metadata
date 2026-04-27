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
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class ServiceLoaderUtilTest {
    private static final String PROVIDER_PROPERTY = MimeTypeRegistryProvider.class.getName();
    private static final String PROVIDER_CLASS_NAME = TestMimeTypeRegistryProvider.class.getName();

    @Test
    void factoryFinderInstantiatesProviderWithContextClassLoader() {
        Thread currentThread = Thread.currentThread();
        ClassLoader originalContextClassLoader = currentThread.getContextClassLoader();
        String previousProvider = System.getProperty(PROVIDER_PROPERTY);
        TrackingClassLoader trackingClassLoader = new TrackingClassLoader(
                originalContextClassLoader,
                PROVIDER_CLASS_NAME
        );
        TestMimeTypeRegistryProvider.reset();

        System.setProperty(PROVIDER_PROPERTY, PROVIDER_CLASS_NAME);
        currentThread.setContextClassLoader(trackingClassLoader);
        try {
            MimetypesFileTypeMap typeMap = new MimetypesFileTypeMap(
                    mimeTypes("application/x-context-provider ctxprovider")
            );

            assertThat(typeMap.getContentType("sample.ctxprovider")).isEqualTo("application/x-context-provider");
            assertThat(TestMimeTypeRegistryProvider.constructedCount()).isPositive();
            assertThat(trackingClassLoader.loadedClassNames()).contains(PROVIDER_CLASS_NAME);
        } finally {
            currentThread.setContextClassLoader(originalContextClassLoader);
            restoreProviderProperty(previousProvider);
        }
    }

    @Test
    void factoryFinderInstantiatesProviderWithClassForNameWhenContextClassLoaderIsNull() {
        Thread currentThread = Thread.currentThread();
        ClassLoader originalContextClassLoader = currentThread.getContextClassLoader();
        String previousProvider = System.getProperty(PROVIDER_PROPERTY);
        TestMimeTypeRegistryProvider.reset();

        System.setProperty(PROVIDER_PROPERTY, PROVIDER_CLASS_NAME);
        currentThread.setContextClassLoader(null);
        try {
            MimetypesFileTypeMap typeMap = new MimetypesFileTypeMap(mimeTypes("application/x-null-loader nullloader"));

            assertThat(typeMap.getContentType("sample.nullloader")).isEqualTo("application/x-null-loader");
            assertThat(TestMimeTypeRegistryProvider.constructedCount()).isPositive();
        } finally {
            currentThread.setContextClassLoader(originalContextClassLoader);
            restoreProviderProperty(previousProvider);
        }
    }

    private static InputStream mimeTypes(String content) {
        return new ByteArrayInputStream((content + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
    }

    private static void restoreProviderProperty(String previousProvider) {
        if (previousProvider == null) {
            System.clearProperty(PROVIDER_PROPERTY);
        } else {
            System.setProperty(PROVIDER_PROPERTY, previousProvider);
        }
    }

    public static final class TestMimeTypeRegistryProvider implements MimeTypeRegistryProvider {
        private static int constructedCount;

        public TestMimeTypeRegistryProvider() {
            constructedCount++;
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

        private static void reset() {
            constructedCount = 0;
        }

        private static int constructedCount() {
            return constructedCount;
        }
    }

    private static final class TestMimeTypeRegistry implements MimeTypeRegistry {
        private final Map<String, String> mimeTypesByExtension;

        private TestMimeTypeRegistry() {
            this.mimeTypesByExtension = new LinkedHashMap<>();
        }

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

    private static final class TrackingClassLoader extends ClassLoader {
        private final String trackedClassName;
        private final List<String> loadedClassNames;

        private TrackingClassLoader(ClassLoader parent, String trackedClassName) {
            super(parent);
            this.trackedClassName = trackedClassName;
            this.loadedClassNames = new ArrayList<>();
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (trackedClassName.equals(name)) {
                loadedClassNames.add(name);
            }
            return super.loadClass(name);
        }

        private List<String> loadedClassNames() {
            return loadedClassNames;
        }
    }
}
