/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop_thirdparty.hadoop_shaded_guava;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.apache.hadoop.thirdparty.com.google.common.io.Resources;
import org.junit.jupiter.api.Test;

public class ResourcesTest {
    private static final String RESOURCE_NAME =
            "org_apache_hadoop_thirdparty/hadoop_shaded_guava/resources-test.txt";
    private static final String RELATIVE_RESOURCE_NAME = "resources-test.txt";
    private static final String RESOURCE_CONTENT = "hadoop shaded guava resource fixture\n";

    @Test
    void getResourceWithClassLoaderFindsNamedResource() throws IOException {
        URL resourceUrl = Resources.getResource(RESOURCE_NAME);

        assertThat(Resources.toString(resourceUrl, StandardCharsets.UTF_8))
                .isEqualTo(RESOURCE_CONTENT);
    }

    @Test
    void getResourceWithContextClassFindsRelativeResource() throws IOException {
        URL resourceUrl = Resources.getResource(ResourcesTest.class, RELATIVE_RESOURCE_NAME);

        assertThat(Resources.toString(resourceUrl, StandardCharsets.UTF_8))
                .isEqualTo(RESOURCE_CONTENT);
    }
}
