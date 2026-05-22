/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.jgroups.Address;
import org.jgroups.protocols.SHARED_LOOPBACK;
import org.jgroups.protocols.relay.RELAY2;
import org.jgroups.protocols.relay.Route;
import org.jgroups.protocols.relay.SiteMasterPicker;
import org.jgroups.protocols.relay.config.RelayConfig;
import org.jgroups.stack.ProtocolStack;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RELAY2Test {
    @Test
    void initializesConfiguredSiteMasterPickerByClassName() throws Exception {
        FirstSiteMasterPicker.resetInstances();
        ProtocolStack stack = new ProtocolStack();
        RELAY2 relay = new RELAY2()
                .site("local")
                .setSiteMasterPickerImpl(FirstSiteMasterPicker.class.getName())
                .addSite("local", new RelayConfig.SiteConfig("local"));
        stack.addProtocols(new SHARED_LOOPBACK(), relay);

        try {
            stack.initProtocolStack();

            assertThat(FirstSiteMasterPicker.instances()).isEqualTo(1);
        } finally {
            stack.destroy();
        }
    }

    public static class FirstSiteMasterPicker implements SiteMasterPicker {
        private static final AtomicInteger INSTANCES = new AtomicInteger();

        public FirstSiteMasterPicker() {
            INSTANCES.incrementAndGet();
        }

        static void resetInstances() {
            INSTANCES.set(0);
        }

        static int instances() {
            return INSTANCES.get();
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
