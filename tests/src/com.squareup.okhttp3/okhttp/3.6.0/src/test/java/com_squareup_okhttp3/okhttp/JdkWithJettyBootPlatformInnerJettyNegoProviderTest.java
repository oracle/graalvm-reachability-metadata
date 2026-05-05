/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_okhttp3.okhttp;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import javax.net.ssl.SSLSocket;
import okhttp3.Protocol;
import okhttp3.internal.platform.Platform;
import org.eclipse.jetty.alpn.ALPN;
import org.junit.jupiter.api.Test;

public class JdkWithJettyBootPlatformInnerJettyNegoProviderTest {
    @Test
    void jettyNegoProviderDelegatesObjectMethodsToInvocationHandler() throws Exception {
        Platform platform = newJettyBootPlatform();
        RecordingSslSocket socket = new RecordingSslSocket();

        platform.configureTlsExtensions(socket, "example.com",
                Arrays.asList(Protocol.HTTP_2, Protocol.HTTP_1_1));
        ALPN.Provider provider = ALPN.get(socket);
        String selectedProtocol = ((ALPN.ClientProvider) provider)
                .selectProtocol(Arrays.asList("spdy/3.1", "h2"));
        String description = provider.toString();

        assertThat(selectedProtocol).isEqualTo("h2");
        assertThat(description).contains("JettyNegoProvider");
    }

    private static Platform newJettyBootPlatform() throws Exception {
        Class<?> platformClass = Class.forName(
                "okhttp3.internal.platform.JdkWithJettyBootPlatform");
        Constructor<?> constructor = platformClass.getDeclaredConstructor(Method.class,
                Method.class, Method.class, Class.class, Class.class);
        constructor.setAccessible(true);
        return (Platform) constructor.newInstance(
                ALPN.class.getMethod("put", SSLSocket.class, ALPN.Provider.class),
                ALPN.class.getMethod("get", SSLSocket.class),
                ALPN.class.getMethod("remove", SSLSocket.class),
                ALPN.ClientProvider.class,
                ALPN.ServerProvider.class);
    }
}
