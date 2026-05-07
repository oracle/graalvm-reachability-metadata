/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package software_amazon_awssdk.netty_nio_client;

import static org.assertj.core.api.Assertions.assertThat;

import io.netty.channel.ChannelFactory;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.resolver.AddressResolverGroup;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.http.nio.netty.internal.DnsResolverLoader;

public class DnsResolverLoaderTest {
    @Test
    void initCreatesNettyDnsAddressResolverGroup() {
        ChannelFactory<DatagramChannel> datagramChannelFactory = NioDatagramChannel::new;
        AddressResolverGroup<InetSocketAddress> resolverGroup = DnsResolverLoader.init(datagramChannelFactory);

        try {
            assertThat(resolverGroup.getClass().getName()).isEqualTo("io.netty.resolver.dns.DnsAddressResolverGroup");
        } finally {
            resolverGroup.close();
        }
    }
}
