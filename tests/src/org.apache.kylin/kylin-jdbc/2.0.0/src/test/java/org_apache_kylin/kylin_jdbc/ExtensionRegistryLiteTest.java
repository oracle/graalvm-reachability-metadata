/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kylin.kylin_jdbc;

import org.apache.kylin.jdbc.shaded.com.google.protobuf.ExtensionLite;
import org.apache.kylin.jdbc.shaded.com.google.protobuf.ExtensionRegistry;
import org.apache.kylin.jdbc.shaded.com.google.protobuf.GeneratedMessage.GeneratedExtension;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ExtensionRegistryLiteTest {
    @Test
    void addsAFullGeneratedExtensionThroughTheLiteRegistryApi() {
        ExtensionRegistry registry = ExtensionRegistry.newInstance();
        GeneratedExtension<?, ?> extension = GeneratedMessageAnonymous4Test.newTextExtension();
        ExtensionLite<?, ?> liteView = extension;

        registry.add(liteView);

        assertThat(registry.findExtensionByName("sample.tag").descriptor).isSameAs(extension.getDescriptor());
    }
}
