/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package flyway;

import org.flywaydb.core.internal.util.FileUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FileUtilsTest {
    private static final String RESOURCE_PATH = "flyway/file-utils-resource.txt";

    @Test
    void readsClasspathResourceAsStringWithProvidedClassLoader() {
        ClassLoader classLoader = FileUtilsTest.class.getClassLoader();

        String content = FileUtils.readResourceAsString(classLoader, RESOURCE_PATH);

        assertThat(content).contains("content from test resource");
    }
}
