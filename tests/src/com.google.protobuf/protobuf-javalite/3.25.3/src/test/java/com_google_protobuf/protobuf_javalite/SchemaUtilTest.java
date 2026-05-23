/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_protobuf.protobuf_javalite;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import org.junit.jupiter.api.Test;

public class SchemaUtilTest {
    @Test
    void serializesAndParsesLiteMapMessage() throws Exception {
        Value value = Value.newBuilder().setStringValue("protobuf-javalite").build();
        Struct message = Struct.newBuilder().putFields("library", value).build();

        Struct parsed = Struct.parseFrom(message.toByteArray());

        assertThat(parsed.getFieldsOrThrow("library").getStringValue())
                .isEqualTo("protobuf-javalite");
    }
}
