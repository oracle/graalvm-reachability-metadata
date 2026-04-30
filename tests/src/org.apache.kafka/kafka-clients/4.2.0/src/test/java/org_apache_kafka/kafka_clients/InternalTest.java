/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.apache.kafka.shaded.com.google.protobuf.Any;
import org.apache.kafka.shaded.com.google.protobuf.ByteString;
import org.apache.kafka.shaded.com.google.protobuf.Internal;
import org.junit.jupiter.api.Test;

public class InternalTest {
    @Test
    void resolvesGeneratedMessageDefaultInstance() {
        Any defaultInstance = Internal.getDefaultInstance(Any.class);

        assertSame(Any.getDefaultInstance(), defaultInstance);
        assertEquals("", defaultInstance.getTypeUrl());
        assertEquals(ByteString.EMPTY, defaultInstance.getValue());
    }
}
