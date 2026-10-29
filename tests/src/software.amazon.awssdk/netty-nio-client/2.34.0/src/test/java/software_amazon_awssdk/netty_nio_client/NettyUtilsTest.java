/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package software_amazon_awssdk.netty_nio_client;

import io.netty.handler.ssl.SslProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import software.amazon.awssdk.http.Protocol;
import software.amazon.awssdk.http.ProtocolNegotiation;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.async.AsyncExecuteRequest;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.http.nio.netty.ProxyConfiguration;
import software.amazon.awssdk.http.nio.netty.SdkEventLoopGroup;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NettyUtilsTest {
    @Test
    @Timeout(120)
    void acceptsJdkAlpnConfigurationAndRejectsPlaintextRequests() {
        try (SdkAsyncHttpClient client = NettyNioAsyncHttpClient.builder()
                .sslProvider(SslProvider.JDK)
                .protocol(Protocol.HTTP2)
                .protocolNegotiation(ProtocolNegotiation.ALPN)
                .eventLoopGroupBuilder(SdkEventLoopGroup.builder().numberOfThreads(1))
                .proxyConfiguration(ProxyConfiguration.builder()
                        .useSystemPropertyValues(false)
                        .useEnvironmentVariableValues(false)
                        .build())
                .build()) {
            AsyncExecuteRequest request = AsyncExecuteRequest.builder()
                    .request(SdkHttpRequest.builder()
                            .uri(URI.create("http://127.0.0.1/message"))
                            .method(SdkHttpMethod.GET)
                            .build())
                    .build();

            UnsupportedOperationException error = assertThrows(UnsupportedOperationException.class,
                    () -> client.execute(request));

            assertTrue(error.getMessage().contains("ALPN can only be used with HTTPS, not HTTP"));
        }
    }
}
