package kotlinreflect

import kotlin.reflect.jvm.internal.impl.metadata.ProtoBuf
import kotlin.reflect.jvm.internal.impl.protobuf.GeneratedMessageLite
import kotlin.reflect.jvm.internal.impl.protobuf.WireFormat
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GeneratedMessageLiteTest {
    @Test
    fun roundTripsEnumExtensionThroughGeneratedMessageApi(): Unit {
        val extension: GeneratedMessageLite.GeneratedExtension<ProtoBuf.Function, ProtoBuf.Visibility> =
            GeneratedMessageLite.newSingularGeneratedExtension(
                ProtoBuf.Function.getDefaultInstance(),
                ProtoBuf.Visibility.PUBLIC,
                null,
                null,
                199,
                WireFormat.FieldType.ENUM,
                ProtoBuf.Visibility::class.java,
            )

        val message: ProtoBuf.Function = ProtoBuf.Function.newBuilder()
            .setExtension(extension, ProtoBuf.Visibility.PUBLIC)
            .buildPartial()

        assertThat(message.hasExtension(extension)).isTrue()
        assertThat(message.getExtension(extension)).isEqualTo(ProtoBuf.Visibility.PUBLIC)
    }
}
