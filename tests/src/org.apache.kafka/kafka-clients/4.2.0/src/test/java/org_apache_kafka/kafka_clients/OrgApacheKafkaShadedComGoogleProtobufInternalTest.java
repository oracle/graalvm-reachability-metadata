/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import org.apache.kafka.shaded.com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import org.apache.kafka.shaded.com.google.protobuf.Internal;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class OrgApacheKafkaShadedComGoogleProtobufInternalTest {

    @Test
    void getDefaultInstanceInvokesGeneratedMessageDefaultInstanceAccessor() {
        FileDescriptorProto defaultInstance = Internal.getDefaultInstance(FileDescriptorProto.class);

        assertThat(defaultInstance).isSameAs(FileDescriptorProto.getDefaultInstance());
        assertThat(defaultInstance.getName()).isEmpty();
        assertThat(defaultInstance.getMessageTypeList()).isEmpty();
    }
}
