/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_curator.curator_client;

import java.net.URL;

import org.apache.curator.shaded.com.google.common.io.Resources;
import org.junit.jupiter.api.Test;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ResourcesTest {
    @Test
    void rejectsMissingResources() {
        assertThatThrownBy(() -> Resources.getResource("missing-curator-resource.txt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing-curator-resource.txt");
    }

    @Test
    void loadsResourceRelativeToContextClass() throws Exception {
        URL resource = Resources.getResource(ResourcesTest.class, "resources-relative.txt");

        assertThat(Resources.toString(resource, UTF_8)).isEqualTo("loaded relative to curator test\n");
    }
}
