/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_okhttp3.okhttp;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import okhttp3.Protocol;
import okhttp3.internal.platform.Platform;
import org.junit.jupiter.api.Test;

public class OptionalMethodTest {
    @Test
    void androidPlatformInvokesOptionalTlsSocketMethods() {
        RecordingSslSocket socket = new RecordingSslSocket();
        Platform platform = Platform.get();

        platform.configureTlsExtensions(socket, "example.com",
                Arrays.asList(Protocol.HTTP_2, Protocol.HTTP_1_1));
        String selectedProtocol = platform.getSelectedProtocol(socket);

        assertThat(socket.useSessionTickets()).isTrue();
        assertThat(socket.hostname()).isEqualTo("example.com");
        assertThat(new String(socket.alpnProtocols(), StandardCharsets.UTF_8))
                .contains("h2", "http/1.1");
        assertThat(selectedProtocol).isEqualTo("h2");
    }
}
