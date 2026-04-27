/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.kafka.shaded.com.google.protobuf.DescriptorProtos;
import org.apache.kafka.shaded.com.google.protobuf.Internal;
import org.junit.jupiter.api.Test;

public class InternalTest {

    @Test
    void getDefaultInstanceInvokesGeneratedAccessorOnMessageLiteType() {
        DescriptorProtos.FileOptions defaultInstance = Internal.getDefaultInstance(DescriptorProtos.FileOptions.class);

        assertThat(defaultInstance).isSameAs(DescriptorProtos.FileOptions.getDefaultInstance());
    }
}
