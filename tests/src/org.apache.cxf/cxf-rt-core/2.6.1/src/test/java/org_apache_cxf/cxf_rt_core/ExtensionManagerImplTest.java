/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_cxf.cxf_rt_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;

import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.bus.CXFBusFactory;
import org.apache.cxf.bus.extension.Extension;
import org.apache.cxf.bus.extension.ExtensionManagerImpl;
import org.apache.cxf.catalog.OASISCatalogManager;
import org.apache.cxf.configuration.ConfiguredBeanLocator;
import org.apache.cxf.endpoint.ServerRegistry;
import org.apache.cxf.headers.HeaderManager;
import org.apache.cxf.phase.PhaseManager;
import org.apache.cxf.resource.ResourceManager;
import org.apache.cxf.workqueue.WorkQueueManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class ExtensionManagerImplTest {
    private Bus bus;

    @AfterEach
    void closeBus() {
        if (bus != null) {
            bus.shutdown(true);
        }
        BusFactory.setDefaultBus(null);
    }

    @Test
    void loadsExtensionDescriptorsAndSuppliesActivationNamespaces() {
        bus = new CXFBusFactory().createBus();
        assertThat(bus.getExtension(PhaseManager.class)).isNotNull();
        assertThat(bus.getExtension(WorkQueueManager.class)).isNotNull();
        assertThat(bus.getExtension(ServerRegistry.class)).isNotNull();
        assertThat(bus.getExtension(HeaderManager.class)).isNotNull();
        assertThat(bus.getExtension(ResourceManager.class)).isNotNull();
        assertThat(bus.getExtension(OASISCatalogManager.class)).isNotNull();

        ExtensionManagerImpl manager =
                (ExtensionManagerImpl) bus.getExtension(ConfiguredBeanLocator.class);
        Extension extension = new Extension(NamespaceAwareExtension.class);
        extension.getNamespaces().add("urn:test:activation");
        manager.add(extension);

        NamespaceAwareExtension loaded =
                manager.getExtension(
                        NamespaceAwareExtension.class.getName(), NamespaceAwareExtension.class);

        assertThat(loaded.activationNamespaces).containsExactly("urn:test:activation");
    }

    public static final class NamespaceAwareExtension {
        private Collection<String> activationNamespaces;

        public NamespaceAwareExtension() { }

        public void setActivationNamespaces(Collection<String> namespaces) {
            activationNamespaces = namespaces;
        }
    }
}
