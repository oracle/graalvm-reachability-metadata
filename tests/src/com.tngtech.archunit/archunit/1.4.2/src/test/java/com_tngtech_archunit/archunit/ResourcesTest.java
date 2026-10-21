/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_tngtech_archunit.archunit;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.thirdparty.com.google.common.io.Resources;
import java.net.URL;
import org.junit.jupiter.api.Test;

public class ResourcesTest {
    @Test
    void getResourceFindsClasspathResourceWithContextClassLoader() throws Exception {
        URL resource = Resources.getResource("com_tngtech_archunit/archunit/resources-classloader.txt");

        assertThat(Resources.toString(resource, UTF_8)).isEqualTo("loaded by classloader\n");
    }

    @Test
    void getResourceFindsRelativeResourceWithContextClass() throws Exception {
        URL resource = Resources.getResource(ResourcesTest.class, "resources-relative.txt");

        assertThat(Resources.toString(resource, UTF_8)).isEqualTo("loaded relative to test class\n");
    }
}
