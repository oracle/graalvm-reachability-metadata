/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_helidon_common.helidon_common_configurable;

import static org.assertj.core.api.Assertions.assertThat;

import io.helidon.common.configurable.Resource;

import org.junit.jupiter.api.Test;

public class ResourceUtilTest {
    private static final String RESOURCE_CLASS_PATH = "io/helidon/common/configurable/Resource.class";

    @Test
    void loadsClasspathResourceThroughPublicFactory() {
        Resource resource = Resource.create(RESOURCE_CLASS_PATH);

        assertThat(resource.sourceType()).isEqualTo(Resource.Source.CLASSPATH);
        assertThat(resource.location()).isEqualTo(RESOURCE_CLASS_PATH);
        assertThat(resource.bytes()).startsWith((byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE);
    }
}
