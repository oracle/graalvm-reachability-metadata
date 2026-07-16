/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_github_classgraph.classgraph;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import nonapi.io.github.classgraph.utils.ProxyingInputStream;

public class ProxyingInputStreamTest {
    @Test
    void delegatesModernInputStreamOperations() throws Exception {
        try (var inputStream = proxyingStream("all")) {
            assertThat(inputStream.readAllBytes()).isEqualTo(bytes("all"));
        }
        try (var inputStream = proxyingStream("bytes")) {
            assertThat(inputStream.readNBytes(3)).isEqualTo(bytes("byt"));
        }
        try (var inputStream = proxyingStream("bytes")) {
            final byte[] buffer = new byte[4];
            assertThat(inputStream.readNBytes(buffer, 1, 3)).isEqualTo(3);
            assertThat(buffer).containsExactly((byte) 0, (byte) 'b', (byte) 'y', (byte) 't');
        }
        try (var inputStream = proxyingStream("skip")) {
            inputStream.skipNBytes(2);
            assertThat(inputStream.read()).isEqualTo((int) 'i');
        }
        try (var inputStream = proxyingStream("transfer"); var outputStream = new ByteArrayOutputStream()) {
            assertThat(inputStream.transferTo(outputStream)).isEqualTo(8L);
            assertThat(outputStream.toByteArray()).isEqualTo(bytes("transfer"));
        }
    }

    private static ProxyingInputStream proxyingStream(final String content) {
        return new ProxyingInputStream(new ByteArrayInputStream(bytes(content)));
    }

    private static byte[] bytes(final String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
