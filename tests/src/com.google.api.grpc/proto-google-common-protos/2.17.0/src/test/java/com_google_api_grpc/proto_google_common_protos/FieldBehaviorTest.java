/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_api_grpc.proto_google_common_protos;

import com.google.api.FieldBehavior;
import com.google.api.FieldBehaviorProto;
import com.google.protobuf.DescriptorProtos.FieldOptions;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.TextFormat;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FieldBehaviorTest {
    @Test
    void roundTripsRepeatedFieldBehaviorAnnotations() throws Exception {
        FieldOptions options = FieldOptions.newBuilder()
                .addExtension(FieldBehaviorProto.fieldBehavior, FieldBehavior.REQUIRED)
                .addExtension(FieldBehaviorProto.fieldBehavior, FieldBehavior.IMMUTABLE)
                .setDeprecated(true)
                .build();
        ExtensionRegistry registry = ExtensionRegistry.newInstance();
        FieldBehaviorProto.registerAllExtensions(registry);

        FieldOptions parsed = FieldOptions.parseFrom(options.toByteArray(), registry);

        assertThat(parsed.getExtensionCount(FieldBehaviorProto.fieldBehavior)).isEqualTo(2);
        assertThat(parsed.getExtension(FieldBehaviorProto.fieldBehavior))
                .containsExactly(FieldBehavior.REQUIRED, FieldBehavior.IMMUTABLE);
        assertThat(parsed.getDeprecated()).isTrue();
        assertThat(parsed).isEqualTo(options);
    }

    @Test
    void parsesAndPrintsNamedFieldBehaviorAnnotations() throws Exception {
        ExtensionRegistry registry = ExtensionRegistry.newInstance();
        FieldBehaviorProto.registerAllExtensions(registry);
        FieldOptions.Builder builder = FieldOptions.newBuilder();

        TextFormat.getParser().merge("""
                [google.api.field_behavior]: OUTPUT_ONLY
                [google.api.field_behavior]: IMMUTABLE
                """, registry, builder);
        FieldOptions options = builder.build();

        assertThat(options.getExtension(FieldBehaviorProto.fieldBehavior))
                .containsExactly(FieldBehavior.OUTPUT_ONLY, FieldBehavior.IMMUTABLE);
        assertThat(TextFormat.printer().printToString(options))
                .contains("[google.api.field_behavior]: OUTPUT_ONLY")
                .contains("[google.api.field_behavior]: IMMUTABLE");
    }
}
