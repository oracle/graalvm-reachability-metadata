/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_ibatis.ibatis_sqlmap;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import com.ibatis.common.resources.Resources;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(120)
public class ResourcesTest {
    private static final String RESOURCE = "org_apache_ibatis/ibatis_sqlmap/resource-message.txt";

    @Test
    void findsClasspathResourcesWithExplicitAndSystemLoaders() throws Exception {
        ClassLoader loader = ResourcesTest.class.getClassLoader();
        try (InputStream stream = Resources.getResourceAsStream(loader, RESOURCE)) {
            assertThat(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("ibatis resource content\n");
        }
        try (InputStream stream = Resources.getResourceAsStream(null, RESOURCE)) {
            assertThat(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("ibatis resource content\n");
        }
        try (InputStream stream = Resources.getResourceURL(loader, RESOURCE).openStream()) {
            assertThat(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("ibatis resource content\n");
        }
        try (InputStream stream = Resources.getResourceURL(null, RESOURCE).openStream()) {
            assertThat(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("ibatis resource content\n");
        }
    }

    @Test
    void createsConfiguredObjectsWithAndWithoutAContextLoader() throws Exception {
        Greeting greeting = (Greeting) Resources.instantiate(Greeting.class.getName());
        assertThat(greeting.message()).isEqualTo("hello");
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        try {
            thread.setContextClassLoader(null);
            Greeting fallback = (Greeting) Resources.instantiate(Greeting.class.getName());
            assertThat(fallback.message()).isEqualTo("hello");
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    public static class Greeting {
        public Greeting() {
        }

        public String message() {
            return "hello";
        }
    }
}
