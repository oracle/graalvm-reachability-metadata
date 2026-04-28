/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_extras_beanshell.bsh;

import bsh.BshClassManager;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class BshClassManagerTest {

    private static final String RESOURCE_PATH = "/beanshell-manager-resource.txt";
    private static final String RESOURCE_NAME = "beanshell-manager-resource.txt";

    @Test
    public void plainClassForNameUsesDefaultClassLookupWhenNoExternalLoaderIsConfigured() throws Exception {
        BshClassManager classManager = new BshClassManager();

        Class<?> loadedClass = classManager.plainClassForName("java.lang.String");

        assertThat(loadedClass).isEqualTo(String.class);
    }

    @Test
    public void plainClassForNameDelegatesToConfiguredExternalClassLoader() throws Exception {
        BshClassManager classManager = new BshClassManager();
        RecordingClassLoader classLoader = new RecordingClassLoader();
        classManager.setClassLoader(classLoader);

        Class<?> loadedClass = classManager.plainClassForName(ExternalLoaderTarget.class.getName());

        assertThat(loadedClass).isEqualTo(ExternalLoaderTarget.class);
        assertThat(classLoader.loadedClassName).isEqualTo(ExternalLoaderTarget.class.getName());
    }

    @Test
    public void getResourceDelegatesToConfiguredExternalClassLoader(
            @TempDir Path temporaryDirectory) throws IOException {
        Path resource = temporaryDirectory.resolve(RESOURCE_NAME);
        Files.writeString(resource, "managed resource", StandardCharsets.UTF_8);
        URL resourceUrl = resource.toUri().toURL();
        BshClassManager classManager = new BshClassManager();
        RecordingClassLoader classLoader = new RecordingClassLoader(resourceUrl);
        classManager.setClassLoader(classLoader);

        URL locatedResource = classManager.getResource(RESOURCE_PATH);

        assertThat(locatedResource).isEqualTo(resourceUrl);
        assertThat(classLoader.resourceName).isEqualTo(RESOURCE_NAME);
    }

    @Test
    public void getResourceFallsBackToInterpreterClassResourceLookupWhenNoExternalLoaderIsConfigured() {
        BshClassManager classManager = new BshClassManager();

        URL missingResource = classManager.getResource("/missing-beanshell-manager-resource.txt");

        assertThat(missingResource).isNull();
    }

    @Test
    public void getResourceAsStreamDelegatesToConfiguredExternalClassLoader() throws IOException {
        BshClassManager classManager = new BshClassManager();
        RecordingClassLoader classLoader = new RecordingClassLoader("streamed resource");
        classManager.setClassLoader(classLoader);

        try (InputStream inputStream = classManager.getResourceAsStream(RESOURCE_PATH)) {
            assertThat(inputStream).isNotNull();
            assertThat(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("streamed resource");
        }
        assertThat(classLoader.streamResourceName).isEqualTo(RESOURCE_NAME);
    }

    public static class ExternalLoaderTarget {
    }

    private static class RecordingClassLoader extends ClassLoader {
        private final URL resourceUrl;
        private final String streamContent;
        private String loadedClassName;
        private String resourceName;
        private String streamResourceName;

        RecordingClassLoader() {
            this(null, null);
        }

        RecordingClassLoader(URL resourceUrl) {
            this(resourceUrl, null);
        }

        RecordingClassLoader(String streamContent) {
            this(null, streamContent);
        }

        RecordingClassLoader(URL resourceUrl, String streamContent) {
            super(BshClassManagerTest.class.getClassLoader());
            this.resourceUrl = resourceUrl;
            this.streamContent = streamContent;
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            loadedClassName = name;
            if (ExternalLoaderTarget.class.getName().equals(name)) {
                return ExternalLoaderTarget.class;
            }
            return super.loadClass(name);
        }

        @Override
        public URL getResource(String name) {
            resourceName = name;
            if (RESOURCE_NAME.equals(name)) {
                return resourceUrl;
            }
            return super.getResource(name);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            streamResourceName = name;
            if (RESOURCE_NAME.equals(name) && streamContent != null) {
                return new ByteArrayInputStream(streamContent.getBytes(StandardCharsets.UTF_8));
            }
            return super.getResourceAsStream(name);
        }
    }
}
