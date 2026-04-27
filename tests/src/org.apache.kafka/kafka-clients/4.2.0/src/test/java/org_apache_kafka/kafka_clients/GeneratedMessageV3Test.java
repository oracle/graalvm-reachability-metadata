/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.kafka.shaded.com.google.protobuf.DescriptorProtos;
import org.apache.kafka.shaded.com.google.protobuf.Descriptors;
import org.apache.kafka.shaded.com.google.protobuf.Message;
import org.junit.jupiter.api.Test;

public class GeneratedMessageV3Test {

    @Test
    void reflectiveFieldAccessUsesGeneratedMessageV3InvocationHelpersForSingularMessageFields() {
        Descriptors.FieldDescriptor optionsField = DescriptorProtos.FieldDescriptorProto.getDescriptor()
            .findFieldByName("options");
        Descriptors.FieldDescriptor packedField = DescriptorProtos.FieldOptions.getDescriptor()
            .findFieldByName("packed");
        DescriptorProtos.FieldOptions initialOptions = DescriptorProtos.FieldOptions.newBuilder()
            .setDeprecated(true)
            .build();

        DescriptorProtos.FieldDescriptorProto.Builder builder = DescriptorProtos.FieldDescriptorProto.newBuilder();
        builder.setField(optionsField, initialOptions);

        assertThat(builder.hasField(optionsField)).isTrue();
        assertThat(builder.getField(optionsField)).isEqualTo(initialOptions);

        Message.Builder optionsBuilder = builder.getFieldBuilder(optionsField);
        optionsBuilder.setField(packedField, true);

        DescriptorProtos.FieldOptions expectedOptions = DescriptorProtos.FieldOptions.newBuilder(initialOptions)
            .setPacked(true)
            .build();
        DescriptorProtos.FieldDescriptorProto message = builder.build();

        assertThat(message.hasField(optionsField)).isTrue();
        assertThat(message.getField(optionsField)).isEqualTo(expectedOptions);
    }
}
