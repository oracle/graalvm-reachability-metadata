/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.google.common.io.FileBackedOutputStream;
import org.junit.jupiter.api.Test;

public class FileBackedOutputStreamTest {
    @Test
    void spillsDataAndReadsItThroughTheByteSource() throws Exception {
        FileBackedOutputStream output = new FileBackedOutputStream(4);
        try {
            output.write("pulsar-data".getBytes(UTF_8));
            output.close();

            assertThat(output.asByteSource().read()).isEqualTo("pulsar-data".getBytes(UTF_8));
        } finally {
            output.reset();
        }
    }
}
