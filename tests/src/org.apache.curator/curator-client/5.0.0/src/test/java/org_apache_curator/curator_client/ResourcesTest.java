/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_curator.curator_client;

import org.apache.curator.shaded.com.google.common.io.Resources;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ResourcesTest {
    @Test
    void rejectsMissingResources() {
        assertThatThrownBy(() -> Resources.getResource("missing-curator-resource.txt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing-curator-resource.txt");
    }
}
