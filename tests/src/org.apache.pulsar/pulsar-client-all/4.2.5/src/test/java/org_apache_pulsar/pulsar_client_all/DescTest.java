/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.javassist.runtime.Desc;
import org.junit.jupiter.api.Test;

public class DescTest {
    @Test
    void resolvesDescriptorsWithDefaultAndContextClassLoaders() {
        assertThat(Desc.getClazz(String.class.getName())).isEqualTo(String.class);
        assertThat(Desc.getParams("(Ljava/lang/String;I)V")).containsExactly(String.class, int.class);

        Desc.setUseContextClassLoaderLocally();
        try {
            assertThat(Desc.getType("[Ljava/lang/String;")).isEqualTo(String[].class);
        } finally {
            Desc.resetUseContextClassLoaderLocally();
        }
    }
}
