/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dnsjava.dnsjava;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.xbill.DNS.spi.DnsjavaInetAddressResolverProvider;

import java.net.InetAddress;
import java.net.spi.InetAddressResolverProvider;
import java.util.ServiceLoader;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(30)
public class DnsjavaInetAddressResolverProviderTest {
    @Test
    void discoversDnsjavaProviderForHostResolution() throws Exception {
        String previous = System.getProperty(DnsjavaInetAddressResolverProvider.ENABLE_SPI);
        System.setProperty(DnsjavaInetAddressResolverProvider.ENABLE_SPI, "true");
        try {
            InetAddressResolverProvider provider = ServiceLoader.load(InetAddressResolverProvider.class)
                    .stream().map(ServiceLoader.Provider::get)
                    .filter(candidate -> "dnsjava".equals(candidate.name()))
                    .findFirst().orElseThrow();
            assertThat(provider.name()).isEqualTo("dnsjava");
            assertThat(InetAddress.getAllByName("localhost"))
                    .isNotEmpty().allMatch(InetAddress::isLoopbackAddress);
        } finally {
            if (previous == null) {
                System.clearProperty(DnsjavaInetAddressResolverProvider.ENABLE_SPI);
            } else {
                System.setProperty(DnsjavaInetAddressResolverProvider.ENABLE_SPI, previous);
            }
        }
    }
}
