/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import org.apache.kafka.shaded.com.google.protobuf.Any;
import org.apache.kafka.shaded.com.google.protobuf.Duration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class OrgApacheKafkaShadedComGoogleProtobufInternalTest {

    @Test
    void checksAnyMessageTypeByMessageClass() throws Exception {
        Duration duration = Duration.newBuilder()
                .setSeconds(42)
                .setNanos(7)
                .build();

        Any packed = Any.pack(duration);

        assertThat(packed.is(Duration.class)).isTrue();
        assertThat(packed.unpack(Duration.class)).isEqualTo(duration);
    }
}
