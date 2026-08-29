/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.boot.model.TypeBeanInstanceProducer;
import org.hibernate.type.spi.TypeBootstrapContext;
import org.hibernate.type.spi.TypeConfiguration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TypeBeanInstanceProducerTest {

    @Test
    public void createsBootstrapAwareAndNoArgumentTypes() {
        TypeBeanInstanceProducer producer = new TypeBeanInstanceProducer(
                new TypeConfiguration()
        );

        BootstrapAwareType aware = producer.produceBeanInstance(BootstrapAwareType.class);
        PlainType plain = producer.produceBeanInstance(PlainType.class);

        assertThat(aware.getContext()).isSameAs(producer);
        assertThat(plain.getName()).isEqualTo("plain");
    }

    public static class BootstrapAwareType {
        private final TypeBootstrapContext context;

        public BootstrapAwareType(TypeBootstrapContext context) {
            this.context = context;
        }

        public TypeBootstrapContext getContext() {
            return context;
        }
    }

    public static class PlainType {
        public String getName() {
            return "plain";
        }
    }
}
