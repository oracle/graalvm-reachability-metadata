/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import org.apache.kafka.shaded.com.google.protobuf.Descriptors;
import org.apache.kafka.shaded.io.opentelemetry.proto.profiles.v1experimental.Profile;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class GeneratedMessageAnonymous4Test {

    @Test
    void loadsGeneratedDescriptorViaFieldAccessorTable() {
        Descriptors.Descriptor descriptor = Profile.getDescriptor();

        assertThat(descriptor.findFieldByName("time_nanos")).isNotNull();
    }
}
