/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.apache.pulsar.shade.org.apache.commons.io.channels.CloseShieldChannel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class CloseShieldChannelHandlerTest {
    @Test
    void proxyForwardsChannelOperations(@TempDir Path temporaryDirectory) throws Exception {
        Path output = temporaryDirectory.resolve("forwarded.bin");
        try (SeekableByteChannel delegate = Files.newByteChannel(
                output, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            SeekableByteChannel shield = CloseShieldChannel.wrap(delegate);

            assertThat(shield.write(ByteBuffer.wrap(new byte[] {4, 5, 6}))).isEqualTo(3);
            assertThat(shield.position()).isEqualTo(3);
            shield.position(0);
            ByteBuffer bytes = ByteBuffer.allocate(3);
            assertThat(shield.read(bytes)).isEqualTo(3);
            assertThat(bytes.array()).containsExactly((byte) 4, (byte) 5, (byte) 6);
        }
    }
}
