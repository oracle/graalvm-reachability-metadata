/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_wildfly_common.wildfly_common;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.wildfly.common.net.CidrAddress;
import org.wildfly.common.net.CidrAddressTable;

public class Wildfly_commonTest {
    @Test
    void cidrAddressTableReturnsMostSpecificMatchingNetwork() throws Exception {
        CidrAddressTable<String> table = new CidrAddressTable<>();
        table.put(cidr("0.0.0.0", 0), "default");
        table.put(cidr("10.0.0.0", 8), "private");
        table.put(cidr("10.20.0.0", 16), "site");

        assertThat(table.get(address("10.20.30.40"))).isEqualTo("site");
        assertThat(table.get(address("10.99.1.2"))).isEqualTo("private");
        assertThat(table.get(address("192.0.2.10"))).isEqualTo("default");
        assertThat(table.getOrDefault(address("203.0.113.7"), "fallback")).isEqualTo("default");
    }

    @Test
    void cidrAddressTableSupportsExactReplacementRemovalAndIteration() throws Exception {
        CidrAddressTable<String> table = new CidrAddressTable<>();
        CidrAddress loopback = cidr("127.0.0.0", 8);
        CidrAddress documentation = cidr("192.0.2.0", 24);

        assertThat(table.putIfAbsent(loopback, "loopback")).isNull();
        assertThat(table.putIfAbsent(loopback, "ignored")).isEqualTo("loopback");
        assertThat(table.replaceExact(loopback, "local")).isEqualTo("loopback");
        assertThat(table.replaceExact(documentation, "missing")).isNull();
        assertThat(table.put(documentation, "docs")).isNull();

        List<String> values = new ArrayList<>();
        for (CidrAddressTable.Mapping<String> mapping : table) {
            values.add(mapping.getValue());
        }

        assertThat(values).containsExactlyInAnyOrder("local", "docs");
        assertThat(table.get(address("127.0.0.1"))).isEqualTo("local");
        assertThat(table.removeExact(loopback)).isEqualTo("local");
        assertThat(table.get(address("127.0.0.1"))).isNull();
        assertThat(table.size()).isEqualTo(1);
    }

    private static CidrAddress cidr(String address, int netmaskBits) throws UnknownHostException {
        return CidrAddress.create(address(address), netmaskBits);
    }

    private static InetAddress address(String address) throws UnknownHostException {
        return InetAddress.getByName(address);
    }
}
