/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dnsjava.dnsjava;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.xbill.DNS.Name;
import org.xbill.DNS.ResolverConfig;

import static org.assertj.core.api.Assertions.assertThat;

@ResourceLock(Resources.SYSTEM_PROPERTIES)
public class ResolverConfigTest {
    private String previousServers;
    private String previousSearch;

    @BeforeEach
    public void clearDnsOverrides() {
        previousServers = System.getProperty("dns.server");
        previousSearch = System.getProperty("dns.search");
        System.clearProperty("dns.server");
        System.clearProperty("dns.search");
    }

    @AfterEach
    public void restoreDnsOverrides() {
        restoreProperty("dns.server", previousServers);
        restoreProperty("dns.search", previousSearch);
        ResolverConfig.refresh();
    }

    @Test
    public void discoversSystemNameServers() {
        ResolverConfig config = new ResolverConfig();

        assertThat(config.servers()).isNotEmpty().doesNotHaveDuplicates();
        for (String server : config.servers()) {
            assertThat(server).isNotBlank();
        }
        assertThat(config.server()).isEqualTo(config.servers()[0]);
        assertThat(config.ndots()).isPositive();
    }

    @Test
    public void retainsExplicitSearchPathWhileDiscoveringSystemNameServers() throws Exception {
        System.setProperty("dns.search", "service.example,example,service.example");

        ResolverConfig config = new ResolverConfig();

        assertThat(config.servers()).isNotEmpty().doesNotHaveDuplicates();
        assertThat(config.server()).isEqualTo(config.servers()[0]);
        assertThat(config.searchPath()).containsExactly(
                Name.fromString("service.example."), Name.fromString("example."));
    }

    @Test
    public void refreshReplacesExplicitServersWithSystemDiscovery() throws Exception {
        System.setProperty("dns.server", "192.0.2.53");
        System.setProperty("dns.search", "example");
        ResolverConfig.refresh();
        ResolverConfig explicit = ResolverConfig.getCurrentConfig();
        assertThat(explicit.servers()).containsExactly("192.0.2.53");

        System.clearProperty("dns.server");
        ResolverConfig.refresh();
        ResolverConfig discovered = ResolverConfig.getCurrentConfig();

        assertThat(discovered).isNotSameAs(explicit);
        assertThat(discovered.servers()).isNotEmpty().doesNotHaveDuplicates();
        assertThat(discovered.server()).isEqualTo(discovered.servers()[0]);
        assertThat(discovered.searchPath()).containsExactly(Name.fromString("example."));
        assertThat(explicit.servers()).containsExactly("192.0.2.53");
    }

    private static void restoreProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }
}
