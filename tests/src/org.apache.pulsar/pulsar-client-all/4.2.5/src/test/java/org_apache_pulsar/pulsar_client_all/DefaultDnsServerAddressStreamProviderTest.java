/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import org.apache.pulsar.shade.io.netty.resolver.dns.DefaultDnsServerAddressStreamProvider;
import org.apache.pulsar.shade.io.netty.resolver.dns.DnsServerAddressStream;
import org.junit.jupiter.api.Test;

public class DefaultDnsServerAddressStreamProviderTest {
    @Test
    void suppliesConfiguredOrPlatformDefaultNameServer() {
        DnsServerAddressStream stream =
                DefaultDnsServerAddressStreamProvider.INSTANCE.nameServerAddressStream("localhost");
        InetSocketAddress address = stream.next();

        assertThat(address).isNotNull();
        assertThat(address.getPort()).isPositive();
    }
}
