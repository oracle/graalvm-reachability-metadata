/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jdbi.jdbi3_core;

import org.jdbi.v3.core.extension.ExtensionMetadata;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ExtensionMetadataInnerBuilderTest {
    @Test
    void inspectsInheritedAndDeclaredExtensionMethods() {
        ExtensionMetadata metadata = ExtensionMetadata.builder(ChildExtension.class).build();

        assertThat(metadata.extensionType()).isEqualTo(ChildExtension.class);
        assertThat(metadata.getExtensionMethods())
                .extracting(method -> method.getName())
                .contains("parentValue", "childValue");
    }

    public interface ParentExtension {
        String parentValue();
    }

    public interface ChildExtension extends ParentExtension {
        String childValue();
    }
}
