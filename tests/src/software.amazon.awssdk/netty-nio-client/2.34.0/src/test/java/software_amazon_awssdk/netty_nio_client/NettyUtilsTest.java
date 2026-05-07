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
import software.amazon.awssdk.http.nio.netty.internal.utils.NettyUtils;

public class NettyUtilsTest {
    @Test
    void jdkAlpnSupportUsesSslEngineApplicationProtocolLookup() {
        assertThat(NettyUtils.isAlpnSupported(SslProvider.JDK)).isTrue();
    }
}
