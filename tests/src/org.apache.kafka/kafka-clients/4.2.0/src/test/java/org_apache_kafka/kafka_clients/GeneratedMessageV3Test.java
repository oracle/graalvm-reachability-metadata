/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import org.apache.kafka.shaded.com.google.protobuf.Any;
import org.apache.kafka.shaded.com.google.protobuf.Descriptors;
import org.apache.kafka.shaded.com.google.protobuf.StringValue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class GeneratedMessageV3Test {

    @Test
    void packsParsesUnpacksAndPrintsGeneratedProtobufMessage() throws Exception {
        StringValue value = StringValue.of("payload");
        Any packed = Any.pack(value);
        byte[] bytes = packed.toByteArray();

        Any parsed = Any.parseFrom(bytes);
        StringValue unpacked = parsed.unpack(StringValue.class);
        Descriptors.Descriptor descriptor = StringValue.getDescriptor();

        assertThat(unpacked.getValue()).isEqualTo("payload");
        assertThat(parsed.toString()).contains("type.googleapis.com");
        assertThat(descriptor.findFieldByName("value")).isNotNull();
    }
}
