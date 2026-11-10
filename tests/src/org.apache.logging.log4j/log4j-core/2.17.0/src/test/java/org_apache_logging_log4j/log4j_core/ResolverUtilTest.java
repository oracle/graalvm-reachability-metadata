/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_core;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.core.config.plugins.util.ResolverUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ResolverUtilTest {
    @TempDir
    Path directory;

    @Test
    void discoversConfigurationResourcesUsingAnApplicationResourceLoader() throws Exception {
        Path folder = Files.createDirectory(directory.resolve("configuration"));
        Path resource = Files.writeString(folder.resolve("request.properties"), "level=info");
        ResolverUtil resolver = new ResolverUtil();
        resolver.setClassLoader(new ConfigurationResourceLoader(folder.toUri().toURL(), resource.toUri().toURL()));
        resolver.findInPackage(new PropertiesResourceTest(), "configuration");
        assertEquals(Set.of(resource.toUri()), resolver.getResources());
        assertEquals("level=info", Files.readString(Path.of(resolver.getResources().iterator().next())));
    }

    // This loader serves application files only; class loading always delegates to the existing image classes.
    private static class ConfigurationResourceLoader extends ClassLoader {
        private final URL folder;
        private final URL resource;

        ConfigurationResourceLoader(URL folder, URL resource) {
            super(ResolverUtilTest.class.getClassLoader());
            this.folder = folder;
            this.resource = resource;
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            return name.equals("configuration") ? Collections.enumeration(List.of(folder)) : super.getResources(name);
        }

        @Override
        public URL getResource(String name) {
            return name.equals("configuration/request.properties") ? resource : super.getResource(name);
        }
    }

    private static class PropertiesResourceTest implements ResolverUtil.Test {
        @Override
        public boolean matches(Class<?> type) {
            return false;
        }

        @Override
        public boolean matches(URI resource) {
            return resource.getPath().endsWith(".properties");
        }

        @Override
        public boolean doesMatchClass() {
            return false;
        }

        @Override
        public boolean doesMatchResource() {
            return true;
        }
    }
}
