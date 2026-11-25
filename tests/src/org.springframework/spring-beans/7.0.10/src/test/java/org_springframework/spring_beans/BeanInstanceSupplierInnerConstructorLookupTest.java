/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.aot.BeanInstanceSupplier;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;

import static org.assertj.core.api.Assertions.assertThat;

public class BeanInstanceSupplierInnerConstructorLookupTest {
    @Test
    void constructsBeanThroughPrivateConstructorWithConvertedArguments() {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        RootBeanDefinition definition = new RootBeanDefinition(Manifest.class);
        definition.getConstructorArgumentValues().addIndexedArgumentValue(0, "dispatch");
        definition.getConstructorArgumentValues().addIndexedArgumentValue(1, "3");
        definition.setInstanceSupplier(BeanInstanceSupplier.<Manifest>forConstructor(String.class, int.class));
        factory.registerBeanDefinition("manifest", definition);

        Manifest manifest = factory.getBean("manifest", Manifest.class);

        assertThat(manifest.destination()).isEqualTo("dispatch");
        assertThat(manifest.parcels()).isEqualTo(3);
    }

    public static class Manifest {
        private final String destination;
        private final int parcels;

        private Manifest(String destination, int parcels) {
            this.destination = destination;
            this.parcels = parcels;
        }

        public String destination() {
            return this.destination;
        }

        public int parcels() {
            return this.parcels;
        }
    }
}
