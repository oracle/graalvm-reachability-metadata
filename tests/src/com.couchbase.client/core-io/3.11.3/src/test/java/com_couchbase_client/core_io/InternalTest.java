/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.com.google.protobuf.Internal;
import com.couchbase.client.core.deps.com.google.protobuf.Struct;
import com.couchbase.client.core.deps.com.google.protobuf.Value;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class InternalTest {
    @Test
    void exercisesPublicApi() throws Exception {
        Struct defaultValue = Internal.getDefaultInstance(Struct.class);
        assertSame(Struct.getDefaultInstance(), defaultValue);
        assertEquals(0, defaultValue.getFieldsCount());
        Struct original = Struct.newBuilder().putFields("message", Value.newBuilder().setStringValue("hello").build()).build();
        assertEquals(original, defaultValue.getParserForType().parseFrom(original.toByteArray()));
    }
}
