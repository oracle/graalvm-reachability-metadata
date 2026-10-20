/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kylin.kylin_jdbc;

import org.apache.kylin.jdbc.shaded.com.google.protobuf.GeneratedMessage.GeneratedExtension;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class GeneratedMessageAnonymous4Test {
    @Test
    void resolvesAFileScopedExtensionFromItsDescriptorHolder() {
        GeneratedExtension<?, ?> extension = GeneratedMessageTest.newEnumExtension();

        assertThat(extension.getDescriptor())
                .isSameAs(GeneratedMessageTest.EnumExtensionDescriptor.descriptor.findExtensionByName("enum_tag"));
        assertThat(extension.getNumber()).isEqualTo(50000);
    }
}
