/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_metadata_jvm

import kotlinx.metadata.internal.metadata.ProtoBuf
import kotlinx.metadata.internal.protobuf.GeneratedMessageLite
import kotlinx.metadata.internal.protobuf.WireFormat
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

public class GeneratedMessageLiteTest {
    @Test
    public fun enumGeneratedExtensionRoundTripsThroughFieldSetStorage(): Unit {
        val visibilityExtension: GeneratedMessageLite.GeneratedExtension<ProtoBuf.Property, ProtoBuf.Visibility> =
            GeneratedMessageLite.newSingularGeneratedExtension(
                ProtoBuf.Property.getDefaultInstance(),
                ProtoBuf.Visibility.PUBLIC,
                null,
                null,
                TEST_EXTENSION_FIELD_NUMBER,
                WireFormat.FieldType.ENUM,
                ProtoBuf.Visibility::class.java,
            )

        val property: ProtoBuf.Property = ProtoBuf.Property.newBuilder()
            .setExtension(visibilityExtension, ProtoBuf.Visibility.PRIVATE_TO_THIS)
            .buildPartial()

        assertThat(property.hasExtension(visibilityExtension)).isTrue()
        assertThat(property.getExtension(visibilityExtension)).isEqualTo(ProtoBuf.Visibility.PRIVATE_TO_THIS)
    }

    private companion object {
        private const val TEST_EXTENSION_FIELD_NUMBER: Int = 900_001
    }
}
