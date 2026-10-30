/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_shared.maven_shared_io;

import org.apache.maven.shared.io.location.ClasspathResourceLocatorStrategy;
import org.apache.maven.shared.io.location.Location;
import org.apache.maven.shared.io.logging.DefaultMessageHolder;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

public class ClasspathResourceLocatorStrategyTest {
    @Test
    void resolvesClasspathResourceAndReadsItsContents() throws Exception {
        String resource = "META-INF/maven/org.apache.maven.shared/maven-shared-io/pom.properties";
        ClasspathResourceLocatorStrategy strategy = new ClasspathResourceLocatorStrategy();
        DefaultMessageHolder messages = new DefaultMessageHolder();

        Location location = strategy.resolve(resource, messages);

        assertThat(location).isNotNull();
        assertThat(location.getSpecification()).isEqualTo(resource);
        assertThat(messages.isEmpty()).isTrue();

        File file = location.getFile();
        try {
            assertThat(file).isFile();
            Properties properties = new Properties();
            try (InputStream input = location.getInputStream()) {
                properties.load(input);
            }
            assertThat(properties.getProperty("groupId")).isEqualTo("org.apache.maven.shared");
            assertThat(properties.getProperty("artifactId")).isEqualTo("maven-shared-io");
        } finally {
            location.close();
            Files.deleteIfExists(file.toPath());
        }
    }

    @Test
    void reportsAnUnresolvableClasspathResource() {
        String resource = "maven-shared-io/missing-resource.properties";
        ClasspathResourceLocatorStrategy strategy = new ClasspathResourceLocatorStrategy();
        DefaultMessageHolder messages = new DefaultMessageHolder();

        Location location = strategy.resolve(resource, messages);

        assertThat(location).isNull();
        assertThat(messages.size()).isEqualTo(1);
        assertThat(messages.render()).contains("Failed to resolve classpath resource: " + resource);
    }
}
