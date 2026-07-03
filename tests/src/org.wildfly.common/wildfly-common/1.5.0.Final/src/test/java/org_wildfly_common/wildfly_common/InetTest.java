/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_wildfly_common.wildfly_common;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.Inet4Address;

import org.junit.jupiter.api.Test;
import org.wildfly.common.net.Inet;

public class InetTest {
    @Test
    void resolvesAllAddressesUsingTheRequestedConcreteAddressType() throws Exception {
        Inet4Address[] addresses = Inet.getAllAddressesByNameAndType("127.0.0.1", Inet4Address.class);

        assertThat(addresses).hasSize(1);
        assertThat(addresses[0].getHostAddress()).isEqualTo("127.0.0.1");
    }
}
