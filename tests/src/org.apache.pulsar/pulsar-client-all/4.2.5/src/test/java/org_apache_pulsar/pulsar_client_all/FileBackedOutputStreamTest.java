/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.apache.pulsar.shade.com.google.common.io.FileBackedOutputStream;
import org.junit.jupiter.api.Test;

public class FileBackedOutputStreamTest {

    @Test
    void spillsToDiskAndResetsTheReadableView() throws Exception {
        final FileBackedOutputStream output = new FileBackedOutputStream(4, false);
        try {
            output.write("pulsar-client".getBytes(StandardCharsets.UTF_8));
            output.close();
            assertThat(output.asByteSource().read()).asString(StandardCharsets.UTF_8).isEqualTo("pulsar-client");

            output.reset();
            assertThat(output.asByteSource().isEmpty()).isTrue();
        } finally {
            output.reset();
        }
    }
}
