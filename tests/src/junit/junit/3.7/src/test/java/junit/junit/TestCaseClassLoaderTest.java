/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package junit.junit;

import java.io.InputStream;
import java.net.URL;

import junit.runner.TestCaseClassLoader;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TestCaseClassLoaderTest {
    @Test
    void delegatesExcludedClassesAndResourcesToTheSystemLoader() throws Exception {
        TestCaseClassLoader loader = new TestCaseClassLoader();

        assertThat(loader.isExcluded(String.class.getName())).isTrue();
        assertThat(loader.loadClass(String.class.getName(), false)).isSameAs(String.class);

        String resourceName = "junit/runner/excluded.properties";
        URL resource = loader.getResource(resourceName);
        assertThat(resource).isNotNull();
        try (InputStream stream = loader.getResourceAsStream(resourceName)) {
            assertThat(stream).isNotNull();
            assertThat(stream.read()).isNotEqualTo(-1);
        }
    }
}
