/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.kafka.shaded.com.google.protobuf.Any;
import org.junit.jupiter.api.Test;

public class OrgApacheKafkaShadedComGoogleProtobufInternalTest {

    @Test
    void anyTypeChecksResolveDefaultMessageInstanceByClass() throws Exception {
        Any nestedMessage = Any.getDefaultInstance();
        Any envelope = Any.pack(nestedMessage);

        assertThat(envelope.is(Any.class)).isTrue();
        assertThat(envelope.unpack(Any.class)).isEqualTo(nestedMessage);
    }
}
