/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dnsjava.dnsjava;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.xbill.DNS.Name;
import org.xbill.DNS.config.SunJvmResolverConfigProvider;

import java.net.InetSocketAddress;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(30)
public class SunJvmResolverConfigProviderTest {
    @Test
    void loadsSystemResolverConfigurationAndRefreshesWithoutDuplicates() throws Exception {
        SunJvmResolverConfigProvider provider = new SunJvmResolverConfigProvider();

        provider.initialize();

        List<InetSocketAddress> servers = List.copyOf(provider.servers());
        List<Name> searchPaths = List.copyOf(provider.searchPaths());
        assertThat(servers).isNotEmpty().doesNotHaveDuplicates();
        assertThat(servers).allSatisfy(server -> {
            assertThat(server.isUnresolved()).isFalse();
            assertThat(server.getPort()).isEqualTo(53);
            assertThat(server.getAddress().isAnyLocalAddress()).isFalse();
        });
        assertThat(searchPaths).doesNotHaveDuplicates().allMatch(Name::isAbsolute);
        assertThat(provider.ndots()).isEqualTo(1);

        provider.initialize();

        assertThat(provider.servers()).containsExactlyElementsOf(servers);
        assertThat(provider.searchPaths()).containsExactlyElementsOf(searchPaths);
    }
}
