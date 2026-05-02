/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package xerces.xmlParserAPIs;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class TransformSecuritySupportTest {
    private static final String SECURITY_SUPPORT_CLASS = "javax.xml.transform.SecuritySupport";
    private static final String RESOURCE_NAME = "xerces/xmlParserAPIs/transform-security-support-resource.txt";
    private static final String RESOURCE_CONTENT = "resource loaded through transform SecuritySupport";
    private static final String SYSTEM_RESOURCE_NAME =
            "xerces/xmlParserAPIs/transform-security-support-system-resource.txt";
    private static final String SYSTEM_RESOURCE_CONTENT =
            "system resource loaded through transform SecuritySupport";

    @Test
    void getResourceAsStreamUsesProvidedClassLoader() throws Exception {
        try {
            RecordingResourceClassLoader classLoader = new RecordingResourceClassLoader();
            try (InputStream stream = getResourceAsStream(classLoader, RESOURCE_NAME)) {
                assertThat(stream).isNotNull();
                assertThat(new String(stream.readAllBytes(), UTF_8)).isEqualTo(RESOURCE_CONTENT);
            }

            assertThat(classLoader.requestedResourceName).isEqualTo(RESOURCE_NAME);
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    @Test
    void getResourceAsStreamUsesSystemResourcesWhenClassLoaderIsNull() throws Exception {
        try {
            try (InputStream stream = getResourceAsStream(null, SYSTEM_RESOURCE_NAME)) {
                assertThat(stream).isNotNull();
                assertThat(new String(stream.readAllBytes(), UTF_8)).isEqualTo(SYSTEM_RESOURCE_CONTENT);
            }
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static InputStream getResourceAsStream(ClassLoader classLoader, String resourceName) throws Exception {
        try (URLClassLoader xmlParserApisClassLoader = new URLClassLoader(
                new URL[] { xmlParserApisClassesLocation().toUri().toURL() },
                TransformSecuritySupportTest.class.getClassLoader())) {
            Object securitySupport = newSecuritySupport(xmlParserApisClassLoader);
            Method getResourceAsStream = securitySupport.getClass()
                    .getDeclaredMethod("getResourceAsStream", ClassLoader.class, String.class);
            getResourceAsStream.setAccessible(true);
            return (InputStream) getResourceAsStream.invoke(securitySupport, classLoader, resourceName);
        }
    }

    private static Object newSecuritySupport(ClassLoader classLoader) throws Exception {
        Class<?> securitySupportClass = Class.forName(SECURITY_SUPPORT_CLASS, true, classLoader);
        Constructor<?> constructor = securitySupportClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static Path xmlParserApisClassesLocation() throws Exception {
        Optional<Path> instrumentedClasses = findInstrumentedClasses();
        if (instrumentedClasses.isPresent()) {
            return instrumentedClasses.get();
        }

        String[] classPathEntries = System.getProperty("java.class.path", "").split(File.pathSeparator);
        for (String entry : classPathEntries) {
            Path path = Path.of(entry);
            Path fileName = path.getFileName();
            String normalizedEntry = entry.replace(File.separatorChar, '/');
            if (fileName != null
                    && fileName.toString().startsWith("xmlParserAPIs-")
                    && fileName.toString().endsWith(".jar")
                    && normalizedEntry.contains("/xerces/xmlParserAPIs/")) {
                return path;
            }
        }
        throw new IllegalStateException("xmlParserAPIs classes not found for the test class loader");
    }

    private static Optional<Path> findInstrumentedClasses() throws Exception {
        Path effectiveClassesRoot = Path.of("build", "jacoco", "effective");
        if (!Files.isDirectory(effectiveClassesRoot)) {
            return Optional.empty();
        }
        try (Stream<Path> paths = Files.find(
                effectiveClassesRoot,
                5,
                (path, attributes) -> path.endsWith("javax/xml/transform/SecuritySupport.class"))) {
            return paths.findFirst().map(path -> path.getParent().getParent().getParent().getParent());
        }
    }

    private static final class RecordingResourceClassLoader extends ClassLoader {
        private String requestedResourceName;

        private RecordingResourceClassLoader() {
            super(TransformSecuritySupportTest.class.getClassLoader());
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            requestedResourceName = name;
            if (RESOURCE_NAME.equals(name)) {
                return new ByteArrayInputStream(RESOURCE_CONTENT.getBytes(UTF_8));
            }
            return null;
        }
    }
}
