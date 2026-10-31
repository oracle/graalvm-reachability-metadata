/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_quarkus.quarkus_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkus.runtime.configuration.ConfigUtils;
import io.quarkus.runtime.configuration.InetSocketAddressConverter;
import io.smallrye.config.SmallRyeConfig;

public class InetSocketAddressConverterTest {
    @Test
    void discoversConverterForResolvedIpAddressesAndUnresolvedHostNames() {
        SmallRyeConfig config = ConfigUtils.emptyConfigBuilder()
                .withDefaultValues(Map.of(
                        "app.ipv4", "127.0.0.1:8181",
                        "app.ipv6", "[::1]:8443",
                        "app.remote", "service.example:9090"))
                .build();

        InetSocketAddress ipv4 = config.getValue("app.ipv4", InetSocketAddress.class);
        assertThat(ipv4.isUnresolved()).isFalse();
        assertThat(ipv4.getAddress().getHostAddress()).isEqualTo("127.0.0.1");
        assertThat(ipv4.getPort()).isEqualTo(8181);

        InetSocketAddress ipv6 = config.getValue("app.ipv6", InetSocketAddress.class);
        assertThat(ipv6.isUnresolved()).isFalse();
        assertThat(ipv6.getAddress().isLoopbackAddress()).isTrue();
        assertThat(ipv6.getPort()).isEqualTo(8443);

        InetSocketAddress remote = config.getValue("app.remote", InetSocketAddress.class);
        assertThat(remote.isUnresolved()).isTrue();
        assertThat(remote.getHostString()).isEqualTo("service.example");
        assertThat(remote.getPort()).isEqualTo(9090);
    }

    @Test
    void supportsHostWithoutPortAndWildcardAddress() {
        InetSocketAddressConverter converter = new InetSocketAddressConverter();
        InetSocketAddress host = converter.convert(" service.example ");
        InetSocketAddress wildcard = converter.convert(":8181");

        assertThat(host.isUnresolved()).isTrue();
        assertThat(host.getPort()).isZero();
        assertThat(wildcard.getAddress().isAnyLocalAddress()).isTrue();
        assertThat(wildcard.getPort()).isEqualTo(8181);
        assertThat(converter.convert(" ")).isNull();
    }
}
