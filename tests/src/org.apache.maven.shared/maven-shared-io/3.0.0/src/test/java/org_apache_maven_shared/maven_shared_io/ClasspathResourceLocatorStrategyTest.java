/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_shared.maven_shared_io;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.maven.shared.io.location.ClasspathResourceLocatorStrategy;
import org.apache.maven.shared.io.location.Location;
import org.apache.maven.shared.io.logging.DefaultMessageHolder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClasspathResourceLocatorStrategyTest {
    private static final String RESOURCE_NAME = "classpath-resource-locator-strategy-test.txt";

    @Test
    void resolvesResourceFromContextClassLoader() throws Exception {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(ClasspathResourceLocatorStrategyTest.class.getClassLoader());

        try {
            DefaultMessageHolder messageHolder = new DefaultMessageHolder();
            ClasspathResourceLocatorStrategy strategy = new ClasspathResourceLocatorStrategy(
                    "cpresource", ".tmp", false);

            Location location = strategy.resolve(RESOURCE_NAME, messageHolder);

            assertThat(location).isNotNull();
            assertThat(location.getSpecification()).isEqualTo(RESOURCE_NAME);
            assertThat(messageHolder.isEmpty()).isTrue();

            try (InputStream inputStream = location.getInputStream()) {
                String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                assertThat(content).isEqualTo("resolved from the test classpath\n");
            } finally {
                location.close();
            }
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    void reportsMissingResourceFromContextClassLoader() {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(ClasspathResourceLocatorStrategyTest.class.getClassLoader());

        try {
            DefaultMessageHolder messageHolder = new DefaultMessageHolder();
            ClasspathResourceLocatorStrategy strategy = new ClasspathResourceLocatorStrategy();

            Location location = strategy.resolve("missing-classpath-resource-locator-strategy-test.txt", messageHolder);

            assertThat(location).isNull();
            assertThat(messageHolder.countMessages()).isEqualTo(1);
            assertThat(messageHolder.render()).contains("Failed to resolve classpath resource");
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }
}
