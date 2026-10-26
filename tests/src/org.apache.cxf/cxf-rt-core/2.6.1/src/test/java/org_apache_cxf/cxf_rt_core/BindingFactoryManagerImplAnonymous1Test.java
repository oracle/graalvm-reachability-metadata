/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_cxf.cxf_rt_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.Collections;

import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.binding.BindingFactory;
import org.apache.cxf.bus.CXFBusImpl;
import org.apache.cxf.bus.managers.BindingFactoryManagerImpl;
import org.apache.cxf.configuration.ConfiguredBeanLocator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class BindingFactoryManagerImplAnonymous1Test {
    private static final String NAMESPACE = "urn:test:legacy-binding";

    private Bus bus;

    @AfterEach
    void closeBus() {
        if (bus != null) {
            bus.shutdown(true);
        }
        BusFactory.setDefaultBus(null);
    }

    @Test
    void discoversLegacyFactoryThroughItsActivationNamespaceMethod() throws Exception {
        bus = new CXFBusImpl();
        BindingFactoryManagerImpl manager = new BindingFactoryManagerImpl(bus);
        LegacyBindingFactory factory = new LegacyBindingFactory();
        bus.setExtension(
                new BindingFactoryManagerTestSupport.Locator(
                        manager, factory, NAMESPACE, false),
                ConfiguredBeanLocator.class);

        BindingFactory selected = manager.getBindingFactory(NAMESPACE);

        assertThat(selected).isSameAs(factory);
    }

    public static final class LegacyBindingFactory
            extends BindingFactoryManagerTestSupport.BaseBindingFactory {
        public Collection<String> getActivationNamespace() {
            return Collections.singletonList(NAMESPACE);
        }
    }
}
