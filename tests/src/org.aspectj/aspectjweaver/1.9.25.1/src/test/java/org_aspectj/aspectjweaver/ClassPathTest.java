/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjweaver;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.aspectj.apache.bcel.util.ClassPath;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassPathTest {
    private static final String RESOURCE_NAME = "org_aspectj/aspectjweaver/classpath-resource";
    private static final String RESOURCE_SUFFIX = ".txt";

    @Test
    void readsResourceFromClassLoaderBeforeSearchingConfiguredClassPath() throws Exception {
        ClassPath classPath = new ClassPath("");

        try (InputStream stream = classPath.getInputStream(RESOURCE_NAME, RESOURCE_SUFFIX)) {
            assertThat(stream).isNotNull();
            String content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(content).contains("loaded through ClassPath.getInputStream");
        }
    }
}
