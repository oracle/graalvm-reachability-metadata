/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.apache.kafka.shaded.com.google.protobuf.CodedInputStream;
import org.apache.kafka.shaded.com.google.protobuf.CodedOutputStream;
import org.junit.jupiter.api.Test;

public class UnsafeUtilTest {
    @Test
    void codedStreamsRoundTripLengthPrefixedString() throws IOException {
        String value = "kafka-clients";
        byte[] buffer = new byte[CodedOutputStream.computeStringSizeNoTag(value)];
        CodedOutputStream output = CodedOutputStream.newInstance(buffer);

        output.writeStringNoTag(value);
        output.flush();

        CodedInputStream input = CodedInputStream.newInstance(buffer, 0, output.getTotalBytesWritten());

        assertThat(input.readString()).isEqualTo(value);
        assertThat(input.isAtEnd()).isTrue();
    }
}
