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

public class BindingFactoryManagerImplAnonymous2Test {
    private static final String NAMESPACE = "urn:test:default-binding";

    private Bus bus;

    @AfterEach
    void closeBus() {
        if (bus != null) {
            bus.shutdown(true);
        }
        BusFactory.setDefaultBus(null);
    }

    @Test
    void discoversFactoryThroughItsDefaultNamespacesField() throws Exception {
        bus = new CXFBusImpl();
        BindingFactoryManagerImpl manager = new BindingFactoryManagerImpl(bus);
        DefaultNamespaceBindingFactory factory = new DefaultNamespaceBindingFactory();
        bus.setExtension(
                new BindingFactoryManagerTestSupport.Locator(
                        manager, factory, NAMESPACE, false),
                ConfiguredBeanLocator.class);

        BindingFactory selected = manager.getBindingFactory(NAMESPACE);

        assertThat(selected).isSameAs(factory);
    }

    public static final class DefaultNamespaceBindingFactory
            extends BindingFactoryManagerTestSupport.BaseBindingFactory {
        public static final Collection<String> DEFAULT_NAMESPACES =
                Collections.singletonList(NAMESPACE);
    }
}
