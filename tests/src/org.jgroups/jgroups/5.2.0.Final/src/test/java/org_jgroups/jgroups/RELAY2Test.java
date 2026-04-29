/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import org.jgroups.Address;
import org.jgroups.PhysicalAddress;
import org.jgroups.protocols.TP;
import org.jgroups.protocols.relay.RELAY2;
import org.jgroups.protocols.relay.Route;
import org.jgroups.protocols.relay.SiteMasterPicker;
import org.jgroups.protocols.relay.config.RelayConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class RELAY2Test {
    private static final String LOCAL_SITE = "local-site";

    @BeforeEach
    void resetRecorders() {
        RecordingSiteMasterPicker.reset();
    }

    @Test
    void configureInstantiatesNamedSiteMasterPicker() throws Exception {
        RELAY2 relay = new RELAY2()
            .site(LOCAL_SITE)
            .setSiteMasterPickerImpl(RecordingSiteMasterPicker.class.getName())
            .addSite(LOCAL_SITE, new RelayConfig.SiteConfig(LOCAL_SITE));
        relay.setDownProtocol(new TestTransport());

        relay.configure();

        assertThat(RecordingSiteMasterPicker.instances()).isEqualTo(1);
        assertThat(relay.siteNames()).containsExactly(LOCAL_SITE);
    }

    public static class RecordingSiteMasterPicker implements SiteMasterPicker {
        private static final AtomicInteger INSTANCES = new AtomicInteger();

        public RecordingSiteMasterPicker() {
            INSTANCES.incrementAndGet();
        }

        public static void reset() {
            INSTANCES.set(0);
        }

        public static int instances() {
            return INSTANCES.get();
        }

        @Override
        public Address pickSiteMaster(List<Address> siteMasters, Address originalSender) {
            return siteMasters.isEmpty() ? null : siteMasters.get(0);
        }

        @Override
        public Route pickRoute(String site, List<Route> routes, Address originalSender) {
            return routes.isEmpty() ? null : routes.get(0);
        }
    }

    public static class TestTransport extends TP {
        @Override
        public boolean supportsMulticasting() {
            return false;
        }

        @Override
        public void sendUnicast(PhysicalAddress dest, byte[] data, int offset, int length) {
        }

        @Override
        public String getInfo() {
            return "test transport";
        }

        @Override
        protected PhysicalAddress getPhysicalAddress() {
            return null;
        }
    }
}
