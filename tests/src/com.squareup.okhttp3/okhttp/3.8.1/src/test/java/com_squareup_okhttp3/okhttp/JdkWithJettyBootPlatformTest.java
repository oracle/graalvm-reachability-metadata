/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_okhttp3.okhttp;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import okhttp3.Protocol;
import okhttp3.internal.platform.Platform;
import org.eclipse.jetty.alpn.ALPN;
import org.junit.jupiter.api.Test;

public class JdkWithJettyBootPlatformTest {
    @Test
    void jettyBootPlatformConfiguresReadsAndRemovesAlpnProvider() throws Exception {
        Platform platform = newJettyBootPlatform();
        RecordingSslSocket socket = new RecordingSslSocket();

        platform.configureTlsExtensions(socket, "example.com",
                Arrays.asList(Protocol.HTTP_2, Protocol.HTTP_1_1));
        ALPN.Provider provider = ALPN.get(socket);
        ((ALPN.ClientProvider) provider).protocolSelected("h2");
        String selectedProtocol = platform.getSelectedProtocol(socket);
        platform.afterHandshake(socket);

        assertThat(provider).isNotNull();
        assertThat(selectedProtocol).isEqualTo("h2");
        assertThat(ALPN.get(socket)).isNull();
    }

    @Test
    void jettyBootPlatformBuildIfSupportedDiscoversAlpnProvider() throws Exception {
        Platform platform = newJettyBootPlatform();

        assertThat(platform).isNotNull();
    }

    private static Platform newJettyBootPlatform() throws Exception {
        Class<?> platformClass = Class.forName(
                "okhttp3.internal.platform.JdkWithJettyBootPlatform");
        Method buildIfSupported = platformClass.getDeclaredMethod("buildIfSupported");
        buildIfSupported.setAccessible(true);
        return (Platform) buildIfSupported.invoke(null);
    }
}
