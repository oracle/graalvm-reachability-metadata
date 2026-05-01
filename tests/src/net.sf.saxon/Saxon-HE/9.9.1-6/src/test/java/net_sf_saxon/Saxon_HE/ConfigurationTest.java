/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_saxon.Saxon_HE;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.ArrayList;
import java.util.List;
import javax.xml.transform.stream.StreamSource;

import net.sf.saxon.Configuration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ConfigurationTest {
    private static final String CONFIGURATION_CLASS_NAME = Configuration.class.getName();
    private static final String RESOURCE_NAME = "net/sf/saxon/data/profile.xsl";
    private static final byte[] RESOURCE_BYTES = "<xsl:stylesheet version='3.0'/>".getBytes();

    @Test
    void newConfigurationReflectivelyCreatesDefaultConfiguration() {
        Configuration configuration = Configuration.newConfiguration();

        assertThat(configuration).isExactlyInstanceOf(Configuration.class);
    }

    @Test
    void instantiateConfigurationUsesSuppliedClassLoader() throws Exception {
        ReturningConfigurationClassLoader loader = new ReturningConfigurationClassLoader();

        Configuration configuration = Configuration.instantiateConfiguration(CONFIGURATION_CLASS_NAME, loader);

        assertThat(configuration).isExactlyInstanceOf(Configuration.class);
        assertThat(loader.loadedClassNames).containsExactly(CONFIGURATION_CLASS_NAME);
    }

    @Test
    void instantiateConfigurationFallsBackToClassForNameWhenClassLoaderCannotLoadClass() throws Exception {
        FailingClassLoader loader = new FailingClassLoader();

        Configuration configuration = Configuration.instantiateConfiguration(CONFIGURATION_CLASS_NAME, loader);

        assertThat(configuration).isExactlyInstanceOf(Configuration.class);
        assertThat(loader.loadedClassNames).containsExactly(CONFIGURATION_CLASS_NAME);
    }

    @Test
    void instantiateConfigurationUsesClassForNameWhenNoClassLoaderIsAvailable() throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader originalLoader = thread.getContextClassLoader();
        thread.setContextClassLoader(null);
        try {
            Configuration configuration = Configuration.instantiateConfiguration(CONFIGURATION_CLASS_NAME, null);

            assertThat(configuration).isExactlyInstanceOf(Configuration.class);
        } finally {
            thread.setContextClassLoader(originalLoader);
        }
    }

    @Test
    void locateResourceReadsFromContextClassLoader() throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader originalLoader = thread.getContextClassLoader();
        InMemoryResourceClassLoader loader = new InMemoryResourceClassLoader(true);
        thread.setContextClassLoader(loader);
        try {
            List<String> messages = new ArrayList<>();
            List<ClassLoader> loaders = new ArrayList<>();

            try (InputStream input = Configuration.locateResource("profile.xsl", messages, loaders)) {
                assertThat(input).isNotNull();
                assertThat(input.readAllBytes()).isEqualTo(RESOURCE_BYTES);
            }
            assertThat(messages).isEmpty();
            assertThat(loaders).containsExactly(loader);
            assertThat(loader.resourceNames).containsExactly(RESOURCE_NAME);
            assertThat(loader.streamResourceNames).containsExactly(RESOURCE_NAME);
        } finally {
            thread.setContextClassLoader(originalLoader);
        }
    }

    @Test
    void locateResourceFallsThroughToConfigurationLoaderAndSystemResource() {
        Thread thread = Thread.currentThread();
        ClassLoader originalLoader = thread.getContextClassLoader();
        MissingResourceClassLoader loader = new MissingResourceClassLoader();
        thread.setContextClassLoader(loader);
        try {
            List<String> messages = new ArrayList<>();
            List<ClassLoader> loaders = new ArrayList<>();

            InputStream input = Configuration.locateResource("missing-saxon-resource.xml", messages, loaders);

            assertThat(input).isNull();
            assertThat(messages).isNotEmpty();
            assertThat(loaders).isNotEmpty();
            assertThat(loader.resourceNames).containsExactly("net/sf/saxon/data/missing-saxon-resource.xml");
            assertThat(loader.streamResourceNames).containsExactly("net/sf/saxon/data/missing-saxon-resource.xml");
        } finally {
            thread.setContextClassLoader(originalLoader);
        }
    }

    @Test
    void locateResourceSourceReadsFromContextClassLoader() throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader originalLoader = thread.getContextClassLoader();
        InMemoryResourceClassLoader loader = new InMemoryResourceClassLoader(true);
        thread.setContextClassLoader(loader);
        try {
            List<String> messages = new ArrayList<>();
            List<ClassLoader> loaders = new ArrayList<>();

            StreamSource source = Configuration.locateResourceSource(RESOURCE_NAME, messages, loaders);

            assertThat(source.getSystemId()).isEqualTo(loader.resourceUrl.toString());
            try (InputStream input = source.getInputStream()) {
                assertThat(input).isNotNull();
                assertThat(input.readAllBytes()).isEqualTo(RESOURCE_BYTES);
            }
            assertThat(messages).isEmpty();
            assertThat(loaders).containsExactly(loader);
            assertThat(loader.resourceNames).containsExactly(RESOURCE_NAME);
            assertThat(loader.streamResourceNames).containsExactly(RESOURCE_NAME);
        } finally {
            thread.setContextClassLoader(originalLoader);
        }
    }

    @Test
    void locateResourceSourceFallsThroughToConfigurationLoader() throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader originalLoader = thread.getContextClassLoader();
        InMemoryResourceClassLoader loader = new InMemoryResourceClassLoader(false);
        thread.setContextClassLoader(loader);
        try {
            List<String> messages = new ArrayList<>();
            List<ClassLoader> loaders = new ArrayList<>();

            StreamSource source = Configuration.locateResourceSource(RESOURCE_NAME, messages, loaders);

            assertThat(source.getSystemId()).isEqualTo(loader.resourceUrl.toString());
            InputStream input = source.getInputStream();
            if (input != null) {
                input.close();
            }
            assertThat(messages).isNotEmpty();
            assertThat(loaders).isNotEmpty();
            assertThat(loader.resourceNames).containsExactly(RESOURCE_NAME);
            assertThat(loader.streamResourceNames).containsExactly(RESOURCE_NAME);
        } finally {
            thread.setContextClassLoader(originalLoader);
        }
    }

    private static final class ReturningConfigurationClassLoader extends ClassLoader {
        private final List<String> loadedClassNames = new ArrayList<>();

        private ReturningConfigurationClassLoader() {
            super(null);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            loadedClassNames.add(name);
            if (CONFIGURATION_CLASS_NAME.equals(name)) {
                return Configuration.class;
            }
            throw new ClassNotFoundException(name);
        }
    }

    private static final class FailingClassLoader extends ClassLoader {
        private final List<String> loadedClassNames = new ArrayList<>();

        private FailingClassLoader() {
            super(null);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            loadedClassNames.add(name);
            throw new ClassNotFoundException(name);
        }
    }

    private static class InMemoryResourceClassLoader extends ClassLoader {
        private final boolean streamsAvailable;
        protected final List<String> resourceNames = new ArrayList<>();
        protected final List<String> streamResourceNames = new ArrayList<>();
        private final URL resourceUrl;

        private InMemoryResourceClassLoader(boolean streamsAvailable) {
            super(null);
            this.streamsAvailable = streamsAvailable;
            this.resourceUrl = newResourceUrl();
        }

        @Override
        public URL getResource(String name) {
            resourceNames.add(name);
            return resourceUrl;
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            streamResourceNames.add(name);
            if (streamsAvailable) {
                return new ByteArrayInputStream(RESOURCE_BYTES);
            }
            return null;
        }
    }

    private static final class MissingResourceClassLoader extends InMemoryResourceClassLoader {
        private MissingResourceClassLoader() {
            super(false);
        }

        @Override
        public URL getResource(String name) {
            resourceNames.add(name);
            return null;
        }
    }

    private static URL newResourceUrl() {
        try {
            return new URL(null, "memory://saxon/" + RESOURCE_NAME, new URLStreamHandler() {
                @Override
                protected URLConnection openConnection(URL url) {
                    return new URLConnection(url) {
                        @Override
                        public void connect() throws IOException {
                        }

                        @Override
                        public InputStream getInputStream() {
                            return new ByteArrayInputStream(RESOURCE_BYTES);
                        }
                    };
                }
            });
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot create in-memory resource URL", ex);
        }
    }
}
