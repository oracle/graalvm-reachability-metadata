/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_cxf.cxf_rt_core;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.apache.cxf.binding.Binding;
import org.apache.cxf.binding.BindingFactory;
import org.apache.cxf.bus.managers.BindingFactoryManagerImpl;
import org.apache.cxf.configuration.ConfiguredBeanLocator;
import org.apache.cxf.endpoint.Endpoint;
import org.apache.cxf.service.Service;
import org.apache.cxf.service.model.BindingInfo;
import org.apache.cxf.transport.Destination;

public final class BindingFactoryManagerTestSupport {
    private BindingFactoryManagerTestSupport() {}

    public static final class Locator implements ConfiguredBeanLocator {
        private final BindingFactoryManagerImpl manager;
        private final BindingFactory factory;
        private final String configuredNamespace;
        private final boolean configuredActivation;

        public Locator(
                BindingFactoryManagerImpl manager,
                BindingFactory factory,
                String configuredNamespace,
                boolean configuredActivation) {
            this.manager = manager;
            this.factory = factory;
            this.configuredNamespace = configuredNamespace;
            this.configuredActivation = configuredActivation;
        }

        @Override
        public List<String> getBeanNamesOfType(Class<?> type) {
            return Collections.singletonList("testBindingFactory");
        }

        @Override
        public <T> T getBeanOfType(String name, Class<T> type) {
            return type.cast(factory);
        }

        @Override
        public <T> Collection<? extends T> getBeansOfType(Class<T> type) {
            return Collections.singletonList(type.cast(factory));
        }

        @Override
        public <T> boolean loadBeansOfType(Class<T> type, BeanLoaderListener<T> listener) {
            Class<? extends T> factoryType = factory.getClass().asSubclass(type);
            if (!listener.loadBean("testBindingFactory", factoryType)) {
                return false;
            }
            boolean loaded = listener.beanLoaded("testBindingFactory", type.cast(factory));
            manager.registerBindingFactory(configuredNamespace, factory);
            return loaded;
        }

        @Override
        public boolean hasConfiguredPropertyValue(
                String beanName, String propertyName, String value) {
            return configuredActivation
                    && "activationNamespaces".equals(propertyName)
                    && configuredNamespace.equals(value);
        }

        @Override
        public boolean hasBeanOfName(String name) {
            return "testBindingFactory".equals(name);
        }
    }

    public abstract static class BaseBindingFactory implements BindingFactory {
        @Override
        public Binding createBinding(BindingInfo binding) {
            throw new UnsupportedOperationException();
        }

        @Override
        public BindingInfo createBindingInfo(Service service, String namespace, Object config) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void addListener(Destination destination, Endpoint endpoint) {
            throw new UnsupportedOperationException();
        }
    }
}
