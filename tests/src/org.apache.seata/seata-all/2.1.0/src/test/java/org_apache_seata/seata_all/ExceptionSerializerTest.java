/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_seata.seata_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.seata.saga.engine.serializer.impl.ExceptionSerializer;
import org.junit.jupiter.api.Test;

public class ExceptionSerializerTest {
    @Test
    void serializesAndDeserializesExceptionThroughObjectStreams() {
        Exception original = new Exception("saga state machine failed");
        original.setStackTrace(new StackTraceElement[0]);
        ExceptionSerializer serializer = new ExceptionSerializer();

        byte[] serialized = serializer.serialize(original);
        Exception restored = serializer.deserialize(serialized);

        assertThat(serialized).isNotEmpty();
        assertThat(restored).isNotSameAs(original);
        assertThat(restored).isExactlyInstanceOf(Exception.class);
        assertThat(restored).hasMessage(original.getMessage());
        assertThat(restored.getStackTrace()).isEmpty();
    }
}
