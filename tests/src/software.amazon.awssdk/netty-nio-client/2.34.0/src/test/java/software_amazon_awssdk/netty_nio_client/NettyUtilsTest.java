/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package software_amazon_awssdk.netty_nio_client;

import static org.assertj.core.api.Assertions.assertThat;

import io.netty.handler.ssl.SslProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import software.amazon.awssdk.http.Protocol;
import software.amazon.awssdk.http.ProtocolNegotiation;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;

@Timeout(60)
public class NettyUtilsTest {
    @Test
    void nettyClientBuilderValidatesJdkAlpnSupport() {
        try (SdkAsyncHttpClient client = NettyNioAsyncHttpClient.builder()
                .protocol(Protocol.HTTP2)
                .protocolNegotiation(ProtocolNegotiation.ALPN)
                .sslProvider(SslProvider.JDK)
                .build()) {
            assertThat(client).isNotNull();
        }
    }
}
