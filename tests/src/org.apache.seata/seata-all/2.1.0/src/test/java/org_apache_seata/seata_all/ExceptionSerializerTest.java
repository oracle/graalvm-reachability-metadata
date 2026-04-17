/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_seata.seata_all;

import org.apache.seata.saga.engine.serializer.impl.ExceptionSerializer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ExceptionSerializerTest {
    @Test
    void serializeAndDeserializeRoundTripsExceptions() {
        Exception exception = new IllegalArgumentException("boom", new IllegalStateException("cause"));
        ExceptionSerializer serializer = new ExceptionSerializer();

        byte[] serialized = serializer.serialize(exception);
        Exception deserialized = serializer.deserialize(serialized);

        assertThat(serialized).isNotEmpty();
        assertThat(deserialized)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("boom")
                .hasCauseInstanceOf(IllegalStateException.class)
                .isNotSameAs(exception);
        assertThat(deserialized.getCause()).hasMessage("cause");
    }
}
