/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kylin.kylin_jdbc;

import org.apache.kylin.jdbc.shaded.com.google.protobuf.ExtensionRegistry;
import org.apache.kylin.jdbc.shaded.com.google.protobuf.ExtensionRegistryLite;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ExtensionRegistryFactoryTest {
    @Test
    void createsMutableFullAndLiteExtensionRegistries() {
        ExtensionRegistry full = ExtensionRegistry.newInstance();
        ExtensionRegistryLite lite = ExtensionRegistryLite.newInstance();

        assertThat(full).isNotSameAs(ExtensionRegistry.getEmptyRegistry());
        assertThat(lite).isNotSameAs(ExtensionRegistryLite.getEmptyRegistry());
    }
}
