/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.kafka.shaded.com.google.protobuf.InvalidProtocolBufferException;
import org.apache.kafka.shaded.com.google.protobuf.Struct;
import org.apache.kafka.shaded.com.google.protobuf.Value;
import org.junit.jupiter.api.Test;

public class SchemaUtilTest {

    @Test
    void parsingGeneratedStructMapFieldInitializesSchemaMapDefaultEntryLookup()
        throws InvalidProtocolBufferException {
        Struct original = Struct.newBuilder()
            .putFields("answer", Value.newBuilder().setNumberValue(42.0d).build())
            .build();

        Struct parsed = Struct.parseFrom(original.toByteArray());

        assertThat(parsed.getFieldsMap()).hasSize(1);
        assertThat(parsed.getFieldsOrThrow("answer").getNumberValue()).isEqualTo(42.0d);
    }
}
