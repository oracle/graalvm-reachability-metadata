/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.com.google.protobuf.Descriptors.FieldDescriptor;
import com.couchbase.client.core.deps.com.google.protobuf.Struct;
import com.couchbase.client.core.deps.com.google.protobuf.Value;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class GeneratedMessageTest {
    @Test
    void exercisesPublicApi() throws Exception {
        Struct original = Struct.newBuilder().putFields("message", Value.newBuilder().setStringValue("hello").build()).build();
        Struct restored = Struct.parseFrom(original.toByteArray());
        FieldDescriptor fields = Struct.getDescriptor().findFieldByName("fields");
        assertEquals(1, restored.getRepeatedFieldCount(fields));
        assertEquals(original.getAllFields(), restored.getAllFields());
        assertEquals("hello", restored.getFieldsOrThrow("message").getStringValue());
    }
}
