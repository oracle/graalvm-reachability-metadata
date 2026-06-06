/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_aop;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import org.springframework.aop.aspectj.SimpleAspectInstanceFactory;
import org.springframework.core.Ordered;

public class SimpleAspectInstanceFactoryTest {

    @Test
    void createsNewAspectInstanceUsingDefaultConstructor() {
        SimpleAspectInstanceFactory factory = new SimpleAspectInstanceFactory(CountingAspect.class);

        Object firstAspect = factory.getAspectInstance();
        Object secondAspect = factory.getAspectInstance();

        assertThat(firstAspect).isInstanceOf(CountingAspect.class);
        assertThat(secondAspect).isInstanceOf(CountingAspect.class);
        assertThat(secondAspect).isNotSameAs(firstAspect);
        assertThat(((CountingAspect) firstAspect).instanceNumber()).isEqualTo(1);
        assertThat(((CountingAspect) secondAspect).instanceNumber()).isEqualTo(2);
    }

    @Test
    void exposesConfiguredAspectClassMetadata() {
        SimpleAspectInstanceFactory factory = new SimpleAspectInstanceFactory(CountingAspect.class);

        assertThat(factory.getAspectClass()).isSameAs(CountingAspect.class);
        assertThat(factory.getAspectClassLoader()).isSameAs(CountingAspect.class.getClassLoader());
        assertThat(factory.getOrder()).isEqualTo(Ordered.LOWEST_PRECEDENCE);
    }

    public static class CountingAspect {
        private static int instanceCount;

        private final int instanceNumber;

        public CountingAspect() {
            instanceCount++;
            this.instanceNumber = instanceCount;
        }

        int instanceNumber() {
            return this.instanceNumber;
        }
    }
}
