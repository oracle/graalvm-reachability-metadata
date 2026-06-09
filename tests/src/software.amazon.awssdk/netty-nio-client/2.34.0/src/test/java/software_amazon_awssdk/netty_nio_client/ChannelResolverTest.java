/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package software_amazon_awssdk.netty_nio_client;

import static org.assertj.core.api.Assertions.assertThat;

import io.netty.channel.oio.OioEventLoopGroup;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import software.amazon.awssdk.http.nio.netty.SdkEventLoopGroup;

@Timeout(60)
public class ChannelResolverTest {
    @Test
    void sdkEventLoopGroupResolvesReflectiveOioChannelFactories() throws Exception {
        OioEventLoopGroup eventLoopGroup = new OioEventLoopGroup(1);
        try {
            SdkEventLoopGroup sdkEventLoopGroup = SdkEventLoopGroup.create(eventLoopGroup);

            assertThat(sdkEventLoopGroup.eventLoopGroup()).isSameAs(eventLoopGroup);
            assertThat(sdkEventLoopGroup.channelFactory()).isNotNull();
            assertThat(sdkEventLoopGroup.datagramChannelFactory()).isNotNull();
        } finally {
            assertThat(eventLoopGroup.shutdownGracefully(0, 1, TimeUnit.SECONDS).await(5, TimeUnit.SECONDS)).isTrue();
        }
    }
}
