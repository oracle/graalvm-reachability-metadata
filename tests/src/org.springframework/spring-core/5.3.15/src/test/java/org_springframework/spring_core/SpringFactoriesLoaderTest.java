/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.OrderComparator;
import org.springframework.core.io.support.SpringFactoriesLoader;

public class SpringFactoriesLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsAndInstantiatesFactoriesFromClassLoaderResources() throws IOException {
        URL factoriesResource = writeSpringFactoriesResource("""
                java.lang.Object=org.springframework.core.OrderComparator
                """);
        ClassLoader classLoader = new SpringFactoriesClassLoader(factoriesResource);

        List<Object> factories = SpringFactoriesLoader.loadFactories(Object.class, classLoader);

        assertThat(factories)
                .singleElement()
                .isInstanceOf(OrderComparator.class);
    }

    private URL writeSpringFactoriesResource(String content) throws IOException {
        Path metaInfDirectory = this.tempDir.resolve("META-INF");
        Files.createDirectories(metaInfDirectory);
        Path factoriesFile = metaInfDirectory.resolve("spring.factories");
        Files.writeString(factoriesFile, content, StandardCharsets.ISO_8859_1);
        return factoriesFile.toUri().toURL();
    }

    private static final class SpringFactoriesClassLoader extends ClassLoader {

        private final URL factoriesResource;

        private SpringFactoriesClassLoader(URL factoriesResource) {
            super(SpringFactoriesLoaderTest.class.getClassLoader());
            this.factoriesResource = factoriesResource;
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            if (SpringFactoriesLoader.FACTORIES_RESOURCE_LOCATION.equals(name)) {
                return Collections.enumeration(List.of(this.factoriesResource));
            }
            return super.getResources(name);
        }
    }
}
