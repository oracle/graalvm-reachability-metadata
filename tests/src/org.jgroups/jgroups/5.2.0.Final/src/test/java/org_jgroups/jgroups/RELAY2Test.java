/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import org.jgroups.Address;
import org.jgroups.JChannel;
import org.jgroups.protocols.SHARED_LOOPBACK;
import org.jgroups.protocols.relay.RELAY2;
import org.jgroups.protocols.relay.Route;
import org.jgroups.protocols.relay.SiteMasterPicker;
import org.jgroups.protocols.relay.config.RelayConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class RELAY2Test {
    static {
        configureJGroupsLoopbackDefaults();
    }

    @BeforeAll
    static void configureLoopbackDefaults() {
        configureJGroupsLoopbackDefaults();
    }

    @Test
    void initializesConfiguredSiteMasterPickerByClassName() throws Exception {
        ConfiguredSiteMasterPicker.instances.set(0);
        RELAY2 relay = new RELAY2()
                .site("local")
                .setSiteMasterPickerImpl(ConfiguredSiteMasterPicker.class.getName())
                .addSite("local", new RelayConfig.SiteConfig("local"));

        try (JChannel channel = new JChannel(new SHARED_LOOPBACK(), relay)) {
            RELAY2 configuredRelay = channel.getProtocolStack().findProtocol(RELAY2.class);

            assertThat((Object) configuredRelay).isSameAs(relay);
            assertThat(relay.getSite()).isEqualTo("local");
            assertThat(relay.getSites()).containsExactly("local");
            assertThat(relay.getTimer()).isNotNull();
            assertThat(ConfiguredSiteMasterPicker.instances).hasValue(1);
        }
    }

    private static void configureJGroupsLoopbackDefaults() {
        System.setProperty("jgroups.bind_addr", "127.0.0.1");
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty("jgroups.use.jdk_logger", "true");
    }

    public static class ConfiguredSiteMasterPicker implements SiteMasterPicker {
        private static final AtomicInteger instances = new AtomicInteger();

        public ConfiguredSiteMasterPicker() {
            instances.incrementAndGet();
        }

        @Override
        public Address pickSiteMaster(List<Address> siteMasters, Address originalSender) {
            return siteMasters.get(0);
        }

        @Override
        public Route pickRoute(String site, List<Route> routes, Address originalSender) {
            return routes.get(0);
        }
    }
}
