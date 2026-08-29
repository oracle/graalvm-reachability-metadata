/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.resource.beans.container.spi.BeanContainer;
import org.hibernate.resource.beans.container.spi.ContainedBean;
import org.hibernate.resource.beans.spi.BeanInstanceProducer;
import org.hibernate.resource.beans.spi.ManagedBeanRegistry;
import org.hibernate.resource.beans.spi.ManagedBeanRegistryInitiator;
import org.hibernate.service.spi.ServiceRegistryImplementor;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class ManagedBeanRegistryInitiatorTest {

    @Test
    public void instantiatesAndUsesAConfiguredBeanContainer() {
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder().build();
        try {
            ManagedBeanRegistry managedBeans = ManagedBeanRegistryInitiator.INSTANCE.initiateService(
                    Map.of(AvailableSettings.BEAN_CONTAINER, RecordingBeanContainer.class),
                    (ServiceRegistryImplementor) registry
            );

            String value = managedBeans.getBean(String.class).getBeanInstance();

            assertThat(managedBeans.getBeanContainer()).isInstanceOf(RecordingBeanContainer.class);
            assertThat(value).isEmpty();
            managedBeans.getBeanContainer().stop();
        }
        finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    public static class RecordingBeanContainer implements BeanContainer {
        @Override
        public <B> ContainedBean<B> getBean(
                Class<B> beanType,
                LifecycleOptions lifecycleOptions,
                BeanInstanceProducer fallbackProducer) {
            return new ProducedBean<>(fallbackProducer.produceBeanInstance(beanType));
        }

        @Override
        public <B> ContainedBean<B> getBean(
                String name,
                Class<B> beanType,
                LifecycleOptions lifecycleOptions,
                BeanInstanceProducer fallbackProducer) {
            return new ProducedBean<>(fallbackProducer.produceBeanInstance(name, beanType));
        }

        @Override
        public void stop() {
        }
    }

    private static class ProducedBean<B> implements ContainedBean<B> {
        private final B instance;

        private ProducedBean(B instance) {
            this.instance = instance;
        }

        @Override
        public B getBeanInstance() {
            return instance;
        }
    }
}
