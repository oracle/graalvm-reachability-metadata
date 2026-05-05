/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_okhttp3.okhttp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import okhttp3.Protocol;
import okhttp3.internal.platform.Platform;
import org.junit.jupiter.api.Test;

public class Jdk9PlatformTest {
    @Test
    void configuresApplicationProtocolsOnJdkSslSocket() throws Exception {
        Platform platform = Platform.get();

        try (SSLSocket socket = (SSLSocket) SSLContext.getDefault()
                .getSocketFactory()
                .createSocket()) {
            platform.configureTlsExtensions(socket, "example.com",
                    Arrays.asList(Protocol.HTTP_2, Protocol.HTTP_1_1));

            assertThat(platform.getSelectedProtocol(socket)).isNull();
        }
    }
}
