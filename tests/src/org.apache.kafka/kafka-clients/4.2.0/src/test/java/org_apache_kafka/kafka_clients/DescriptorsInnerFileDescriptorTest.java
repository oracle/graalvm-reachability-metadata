/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import org.apache.kafka.shaded.com.google.protobuf.Descriptors;
import org.apache.kafka.shaded.io.opentelemetry.proto.profiles.v1experimental.ProfilesProto;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DescriptorsInnerFileDescriptorTest {

    @Test
    void resolvesShadedTelemetryFileDescriptorAndDependencies() {
        Descriptors.FileDescriptor descriptor = ProfilesProto.getDescriptor();

        assertThat(descriptor.getPackage()).isEqualTo("opentelemetry.proto.profiles.v1experimental");
        assertThat(descriptor.getMessageTypes())
                .extracting(Descriptors.Descriptor::getName)
                .contains("ProfilesData", "ResourceProfiles", "ScopeProfiles", "ProfileContainer");
        assertThat(descriptor.getDependencies()).isNotEmpty();
    }
}
