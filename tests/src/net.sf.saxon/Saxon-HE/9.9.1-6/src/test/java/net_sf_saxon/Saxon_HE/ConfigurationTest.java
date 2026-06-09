/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_saxon.Saxon_HE;

import net.sf.saxon.Configuration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.xml.transform.stream.StreamSource;

import static org.assertj.core.api.Assertions.assertThat;

public class ConfigurationTest {
    private static final String CONFIGURATION_CLASS_NAME = "net.sf.saxon.Configuration";
    private static final String DATA_RESOURCE = "profile.xsl";
    private static final String FULL_DATA_RESOURCE = "net/sf/saxon/data/" + DATA_RESOURCE;

    @Test
    void createsDefaultConfiguration() {
        Configuration configuration = Configuration.newConfiguration();

        assertThat(configuration).isInstanceOf(Configuration.class);
    }

    @Test
    void instantiatesConfigurationWithProvidedClassLoader() throws Exception {
        Configuration configuration = Configuration.instantiateConfiguration(
                CONFIGURATION_CLASS_NAME,
                Configuration.class.getClassLoader());

        assertThat(configuration).isInstanceOf(Configuration.class);
    }

    @Test
    void instantiatesConfigurationUsingClassForNameFallbackWhenProvidedClassLoaderCannotLoadClass()
            throws Exception {
        ClassLoader rejectingClassLoader = new RejectingClassLoader();

        Configuration configuration = Configuration.instantiateConfiguration(
                CONFIGURATION_CLASS_NAME,
                rejectingClassLoader);

        assertThat(configuration).isInstanceOf(Configuration.class);
    }

    @Test
    void instantiatesConfigurationUsingClassForNameWhenContextClassLoaderIsUnavailable() throws Exception {
        Thread currentThread = Thread.currentThread();
        ClassLoader originalContextClassLoader = currentThread.getContextClassLoader();
        currentThread.setContextClassLoader(null);
        try {
            Configuration configuration = Configuration.instantiateConfiguration(CONFIGURATION_CLASS_NAME, null);

            assertThat(configuration).isInstanceOf(Configuration.class);
        } finally {
            currentThread.setContextClassLoader(originalContextClassLoader);
        }
    }

    @Test
    void locatesPackagedDataResource() throws IOException {
        List<String> messages = new ArrayList<>();
        List<ClassLoader> loaders = new ArrayList<>();

        try (InputStream resource = Configuration.locateResource(DATA_RESOURCE, messages, loaders)) {
            assertThat(resource).isNotNull();
            assertThat(resource.read()).isNotEqualTo(-1);
        }
        assertThat(loaders).isNotEmpty();
    }

    @Test
    void reportsMissingPackagedDataResource() throws IOException {
        List<String> messages = new ArrayList<>();
        List<ClassLoader> loaders = new ArrayList<>();

        try (InputStream resource = Configuration.locateResource(
                "missing-saxon-test-resource.xml", messages, loaders)) {
            assertThat(resource).isNull();
        }
        assertThat(messages).isNotEmpty();
        assertThat(loaders).isNotEmpty();
    }

    @Test
    void locatesResourceSourceWithSystemId() throws IOException {
        List<String> messages = new ArrayList<>();
        List<ClassLoader> loaders = new ArrayList<>();

        StreamSource source = Configuration.locateResourceSource(FULL_DATA_RESOURCE, messages, loaders);

        assertThat(source.getSystemId()).contains(FULL_DATA_RESOURCE);
        try (InputStream resource = source.getInputStream()) {
            assertThat(resource).isNotNull();
            assertThat(resource.read()).isNotEqualTo(-1);
        }
        assertThat(loaders).isNotEmpty();
    }

    @Test
    void locatesResourceSourceFromFallbackClassLoaderWhenContextStreamCannotBeOpened() throws IOException {
        ClassLoader saxonClassLoader = Configuration.class.getClassLoader();
        URL resourceUrl = saxonClassLoader.getResource(FULL_DATA_RESOURCE);
        assertThat(resourceUrl).isNotNull();
        Thread currentThread = Thread.currentThread();
        ClassLoader originalContextClassLoader = currentThread.getContextClassLoader();
        currentThread.setContextClassLoader(new UrlOnlyResourceClassLoader(FULL_DATA_RESOURCE, resourceUrl));
        try {
            List<String> messages = new ArrayList<>();
            List<ClassLoader> loaders = new ArrayList<>();

            StreamSource source = Configuration.locateResourceSource(FULL_DATA_RESOURCE, messages, loaders);

            assertThat(source.getSystemId()).isEqualTo(resourceUrl.toString());
            try (InputStream resource = source.getInputStream()) {
                assertThat(resource).isNotNull();
                assertThat(resource.read()).isNotEqualTo(-1);
            }
            assertThat(messages).isNotEmpty();
            assertThat(loaders).isNotEmpty();
        } finally {
            currentThread.setContextClassLoader(originalContextClassLoader);
        }
    }

    private static final class RejectingClassLoader extends ClassLoader {
        private RejectingClassLoader() {
            super(Configuration.class.getClassLoader());
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            throw new ClassNotFoundException(name);
        }
    }

    private static final class UrlOnlyResourceClassLoader extends ClassLoader {
        private final String resourceName;
        private final URL resourceUrl;

        private UrlOnlyResourceClassLoader(String resourceName, URL resourceUrl) {
            super(Configuration.class.getClassLoader());
            this.resourceName = resourceName;
            this.resourceUrl = resourceUrl;
        }

        @Override
        public URL getResource(String name) {
            if (resourceName.equals(name)) {
                return resourceUrl;
            }
            return super.getResource(name);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            if (resourceName.equals(name)) {
                return null;
            }
            return super.getResourceAsStream(name);
        }
    }
}
