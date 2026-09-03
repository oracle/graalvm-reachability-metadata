/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.resource.beans.internal.FallbackBeanInstanceProducer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FallbackBeanInstanceProducerTest {

    @Test
    public void createsBeansUsingTheirNoArgumentConstructor() {
        ManagedValue value = FallbackBeanInstanceProducer.INSTANCE
                .produceBeanInstance(ManagedValue.class);

        assertThat(value.getValue()).isEqualTo("managed");
    }

    private static class ManagedValue {
        private String getValue() {
            return "managed";
        }
    }
}
