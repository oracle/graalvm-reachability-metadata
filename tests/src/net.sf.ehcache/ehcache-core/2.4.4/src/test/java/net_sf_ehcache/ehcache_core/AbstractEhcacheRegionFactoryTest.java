/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_ehcache.ehcache_core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import net.sf.ehcache.hibernate.EhCacheRegionFactory;
import org.hibernate.cache.CacheException;
import org.hibernate.cache.QueryResultsRegion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class AbstractEhcacheRegionFactoryTest {
    private static final String CONFIGURATION_RESOURCE_PROPERTY = "net.sf.ehcache.configurationResourceName";
    private static final String CONTEXT_CONFIGURATION_RESOURCE = "abstract-ehcache-region-factory-context.xml";
    private static final String MISSING_CONFIGURATION_RESOURCE = "/missing-abstract-ehcache-region-factory.xml";
    private static final String CACHE_NAME = "dynamicAccessRegion";

    @TempDir
    Path tempDirectory;

    @Test
    void startsFactoryFromResourceResolvedByContextClassLoader() throws Exception {
        Path configurationFile = writeConfigurationFile("context-loader-configuration");
        Properties properties = propertiesFor(CONTEXT_CONFIGURATION_RESOURCE);
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        ResourceClassLoader resourceClassLoader = new ResourceClassLoader(
                originalContextClassLoader,
                CONTEXT_CONFIGURATION_RESOURCE,
                configurationFile.toUri().toURL());
        EhCacheRegionFactory factory = new EhCacheRegionFactory(new Properties());

        Thread.currentThread().setContextClassLoader(resourceClassLoader);
        try {
            factory.start(null, properties);

            QueryResultsRegion region = factory.buildQueryResultsRegion(CACHE_NAME, properties);
            region.put("query-key", "query-value");

            assertThat(resourceClassLoader.wasConfigurationRequested()).isTrue();
            assertThat(region.get("query-key")).isEqualTo("query-value");
            assertThat(factory.isMinimalPutsEnabledByDefault()).isTrue();
            assertThat(factory.nextTimestamp()).isPositive();
        } finally {
            factory.stop();
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }

    @Test
    void attemptsFactoryClassResourceLookupWhenContextClassLoaderMisses() {
        Properties properties = propertiesFor(MISSING_CONFIGURATION_RESOURCE);
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        MissingResourceClassLoader resourceClassLoader = new MissingResourceClassLoader(
                originalContextClassLoader,
                MISSING_CONFIGURATION_RESOURCE);
        EhCacheRegionFactory factory = new EhCacheRegionFactory(new Properties());

        Thread.currentThread().setContextClassLoader(resourceClassLoader);
        try {
            assertThatThrownBy(() -> factory.start(null, properties))
                    .isInstanceOf(CacheException.class);
            assertThat(resourceClassLoader.wasConfigurationRequested()).isTrue();
        } finally {
            factory.stop();
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }

    private Path writeConfigurationFile(String configurationName) throws Exception {
        Path configurationFile = tempDirectory.resolve("ehcache-region-factory.xml");
        Files.writeString(configurationFile, """
                <ehcache name=\"%s\"
                         updateCheck=\"false\"
                         monitoring=\"off\">
                    <diskStore path=\"java.io.tmpdir/abstract-ehcache-region-factory-test\"/>
                    <defaultCache maxElementsInMemory=\"10\"
                                  eternal=\"false\"
                                  overflowToDisk=\"false\"
                                  timeToIdleSeconds=\"120\"
                                  timeToLiveSeconds=\"120\"/>
                </ehcache>
                """.formatted(configurationName), StandardCharsets.UTF_8);
        return configurationFile;
    }

    private static Properties propertiesFor(String configurationResourceName) {
        Properties properties = new Properties();
        properties.setProperty(CONFIGURATION_RESOURCE_PROPERTY, configurationResourceName);
        return properties;
    }

    private static final class ResourceClassLoader extends ClassLoader {
        private final String resourceName;
        private final URL resourceUrl;
        private boolean configurationRequested;

        private ResourceClassLoader(ClassLoader parent, String resourceName, URL resourceUrl) {
            super(parent);
            this.resourceName = resourceName;
            this.resourceUrl = resourceUrl;
        }

        @Override
        public URL getResource(String name) {
            if (resourceName.equals(name)) {
                configurationRequested = true;
                return resourceUrl;
            }
            return super.getResource(name);
        }

        private boolean wasConfigurationRequested() {
            return configurationRequested;
        }
    }

    private static final class MissingResourceClassLoader extends ClassLoader {
        private final String resourceName;
        private boolean configurationRequested;

        private MissingResourceClassLoader(ClassLoader parent, String resourceName) {
            super(parent);
            this.resourceName = resourceName;
        }

        @Override
        public URL getResource(String name) {
            if (resourceName.equals(name)) {
                configurationRequested = true;
                return null;
            }
            return super.getResource(name);
        }

        private boolean wasConfigurationRequested() {
            return configurationRequested;
        }
    }
}
