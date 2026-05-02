/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_resolver_dns_classes_macos;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

import io.netty.resolver.dns.DnsServerAddressStream;
import io.netty.resolver.dns.macos.MacOSDnsServerAddressStreamProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Netty_resolver_dns_classes_macosTest {

    @Test
    void availabilityMethodsExposeAConsistentNativeLibraryState() {
        boolean available = MacOSDnsServerAddressStreamProvider.isAvailable();
        Throwable unavailableCause = MacOSDnsServerAddressStreamProvider.unavailabilityCause();

        assertThat(MacOSDnsServerAddressStreamProvider.isAvailable()).isEqualTo(available);
        assertThat(MacOSDnsServerAddressStreamProvider.unavailabilityCause()).isSameAs(unavailableCause);

        if (available) {
            assertThat(unavailableCause).isNull();
            assertThatCode(MacOSDnsServerAddressStreamProvider::ensureAvailability).doesNotThrowAnyException();
        } else {
            assertThat(unavailableCause).isNotNull();
            assertThatThrownBy(MacOSDnsServerAddressStreamProvider::ensureAvailability)
                    .isInstanceOf(UnsatisfiedLinkError.class)
                    .hasMessage("failed to load the required native library")
                    .hasCause(unavailableCause);
        }
    }

    @Test
    void constructorFollowsTheSameAvailabilityContractAsEnsureAvailability() {
        Throwable unavailableCause = MacOSDnsServerAddressStreamProvider.unavailabilityCause();

        if (unavailableCause == null) {
            MacOSDnsServerAddressStreamProvider provider = new MacOSDnsServerAddressStreamProvider();

            assertThat(provider).isNotNull();
        } else {
            assertThatThrownBy(MacOSDnsServerAddressStreamProvider::new)
                    .isInstanceOf(UnsatisfiedLinkError.class)
                    .hasMessage("failed to load the required native library")
                    .hasCause(unavailableCause);
        }
    }

    @Test
    void availableProviderReturnsUsableStreamsForDifferentHostNameShapes() {
        if (MacOSDnsServerAddressStreamProvider.isAvailable()) {
            MacOSDnsServerAddressStreamProvider provider = new MacOSDnsServerAddressStreamProvider();
            List<String> hostNames = List.of("example.com", "service.example.com", "localhost", "example.com.");

            for (String hostName : hostNames) {
                DnsServerAddressStream stream = provider.nameServerAddressStream(hostName);
                DnsServerAddressStream duplicate = stream.duplicate();

                assertThat(stream.size()).isGreaterThan(0);
                assertThat(duplicate.size()).isEqualTo(stream.size());
                assertValidNameServer(stream.next());
                assertValidNameServer(duplicate.next());
            }
        } else {
            assertThatThrownBy(MacOSDnsServerAddressStreamProvider::new)
                    .isInstanceOf(UnsatisfiedLinkError.class)
                    .hasCause(MacOSDnsServerAddressStreamProvider.unavailabilityCause());
        }
    }

    @Test
    void returnedNameServerStreamsCycleThroughAllConfiguredAddresses() {
        if (MacOSDnsServerAddressStreamProvider.isAvailable()) {
            MacOSDnsServerAddressStreamProvider provider = new MacOSDnsServerAddressStreamProvider();
            DnsServerAddressStream stream = provider.nameServerAddressStream("example.com");
            int size = stream.size();

            List<InetSocketAddress> firstCycle = nextNameServers(stream, size);
            List<InetSocketAddress> secondCycle = nextNameServers(stream, size);

            assertThat(size).isGreaterThan(0);
            assertThat(secondCycle).containsExactlyElementsOf(firstCycle);
        } else {
            assertThatThrownBy(MacOSDnsServerAddressStreamProvider::new)
                    .isInstanceOf(UnsatisfiedLinkError.class)
                    .hasCause(MacOSDnsServerAddressStreamProvider.unavailabilityCause());
        }
    }

    private static List<InetSocketAddress> nextNameServers(DnsServerAddressStream stream, int count) {
        List<InetSocketAddress> addresses = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            InetSocketAddress address = stream.next();
            assertValidNameServer(address);
            addresses.add(address);
        }
        return addresses;
    }

    private static void assertValidNameServer(InetSocketAddress address) {
        assertThat(address).isNotNull();
        assertThat(address.isUnresolved()).isFalse();
        assertThat(address.getAddress()).isNotNull();
        assertThat(address.getPort()).isBetween(1, 65535);
    }
}
