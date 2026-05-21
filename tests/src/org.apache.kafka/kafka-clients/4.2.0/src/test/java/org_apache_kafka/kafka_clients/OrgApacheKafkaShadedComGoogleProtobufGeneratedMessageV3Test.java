/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import org.apache.kafka.shaded.com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import org.apache.kafka.shaded.com.google.protobuf.Descriptors.FieldDescriptor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class OrgApacheKafkaShadedComGoogleProtobufGeneratedMessageV3Test {

    @Test
    void fieldAccessUsesGeneratedMessageV3ReflectionAccessors() {
        FieldDescriptor nameField = FileDescriptorProto.getDescriptor().findFieldByName("name");
        FileDescriptorProto message = FileDescriptorProto.newBuilder()
                .setName("generated_message_v3.proto")
                .build();

        assertThat(message.hasField(nameField)).isTrue();
        assertThat(message.getField(nameField)).isEqualTo("generated_message_v3.proto");

        FileDescriptorProto.Builder builder = FileDescriptorProto.newBuilder();
        builder.setField(nameField, "builder_generated_message_v3.proto");

        assertThat(builder.hasField(nameField)).isTrue();
        assertThat(builder.getField(nameField)).isEqualTo("builder_generated_message_v3.proto");

        builder.clearField(nameField);

        assertThat(builder.hasField(nameField)).isFalse();
        assertThat(builder.getField(nameField)).isEqualTo("");
    }
}
