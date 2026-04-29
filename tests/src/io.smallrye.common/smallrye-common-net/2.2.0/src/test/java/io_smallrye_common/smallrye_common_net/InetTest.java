/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_smallrye_common.smallrye_common_net;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.Inet4Address;
import java.net.UnknownHostException;

import org.junit.jupiter.api.Test;

import io.smallrye.common.net.Inet;

public class InetTest {
    @Test
    void resolvesAllAddressesWithRequestedConcreteType() throws UnknownHostException {
        Inet4Address[] addresses = Inet.getAllAddressesByNameAndType("127.0.0.1", Inet4Address.class);

        assertThat(addresses)
                .hasSize(1)
                .allSatisfy(address -> assertThat(address.getHostAddress()).isEqualTo("127.0.0.1"));
    }
}
