/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.objenesis.instantiator.basic.ObjectInputStreamInstantiator;

public class ObjectInputStreamInstantiatorTest {

    @Test
    void createsInstancesThroughMockedObjectInputStreamDeserialization() {
        NonSerializableParent.constructorCalls.set(0);
        SerializableChild.constructorCalls.set(0);

        ObjectInputStreamInstantiator<SerializableChild> instantiator =
                new ObjectInputStreamInstantiator<>(SerializableChild.class);

        SerializableChild firstInstance = instantiator.newInstance();
        SerializableChild secondInstance = instantiator.newInstance();

        assertThat(firstInstance).isNotSameAs(secondInstance);
        assertThat(NonSerializableParent.constructorCalls).hasValue(2);
        assertThat(SerializableChild.constructorCalls).hasValue(0);
        assertThat(firstInstance.parentValue).isEqualTo("created-by-parent");
        assertThat(secondInstance.parentValue).isEqualTo("created-by-parent");
        assertThat(firstInstance.childValue).isNull();
        assertThat(secondInstance.childValue).isNull();
    }

    public static class NonSerializableParent {

        static final AtomicInteger constructorCalls = new AtomicInteger();

        String parentValue;

        public NonSerializableParent() {
            constructorCalls.incrementAndGet();
            this.parentValue = "created-by-parent";
        }
    }

    public static class SerializableChild extends NonSerializableParent implements Serializable {

        private static final long serialVersionUID = 1L;

        static final AtomicInteger constructorCalls = new AtomicInteger();

        String childValue;

        public SerializableChild() {
            constructorCalls.incrementAndGet();
            this.childValue = "created-by-child";
        }
    }
}
