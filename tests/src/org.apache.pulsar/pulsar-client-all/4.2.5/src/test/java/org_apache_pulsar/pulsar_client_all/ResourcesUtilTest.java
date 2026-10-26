/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import org.apache.pulsar.shade.io.netty.util.internal.ResourcesUtil;
import org.junit.jupiter.api.Test;

public class ResourcesUtilTest {
    @Test
    void resolvesAFilePathForAClassRelativeResource() {
        File resource = ResourcesUtil.getFile(ResourcesUtil.class, "ResourcesUtil.class");

        assertThat(resource.getPath()).endsWith("ResourcesUtil.class");
    }
}
