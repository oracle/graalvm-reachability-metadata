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

import org.apache.pulsar.shade.org.apache.commons.io.channels.CloseShieldChannel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class CloseShieldChannelTest {
    @Test
    void wrappedCloseLeavesUnderlyingChannelOpen(@TempDir Path temporaryDirectory) throws Exception {
        Path output = temporaryDirectory.resolve("shielded.bin");
        try (SeekableByteChannel delegate = Files.newByteChannel(output,
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.WRITE)) {
            SeekableByteChannel shield = CloseShieldChannel.wrap(delegate);
            shield.close();

            assertThat(shield.isOpen()).isFalse();
            assertThat(delegate.isOpen()).isTrue();
            assertThat(delegate.write(ByteBuffer.wrap(new byte[] {1, 2, 3}))).isEqualTo(3);
        }
        assertThat(Files.readAllBytes(output)).containsExactly((byte) 1, (byte) 2, (byte) 3);
    }
}
