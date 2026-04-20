/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_objenesis.objenesis;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicInteger;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.objenesis.instantiator.basic.ObjectInputStreamInstantiator;

public class ObjectInputStreamInstantiatorTest {

    @Test
    void createsDistinctSerializableInstancesUsingObjectInputStreamDeserialization() {
        NonSerializableParent.constructorCalls.set(0);
        SerializableChild.constructorCalls.set(0);

        ObjectInputStreamInstantiator<SerializableChild> instantiator =
            new ObjectInputStreamInstantiator<>(SerializableChild.class);

        SerializableChild firstInstance = instantiator.newInstance();
        SerializableChild secondInstance = instantiator.newInstance();

        Assertions.assertThat(firstInstance).isNotNull();
        Assertions.assertThat(secondInstance).isNotNull();
        Assertions.assertThat(secondInstance).isNotSameAs(firstInstance);
        Assertions.assertThat(NonSerializableParent.constructorCalls).hasValue(2);
        Assertions.assertThat(SerializableChild.constructorCalls).hasValue(0);
        Assertions.assertThat(firstInstance.parentState).isEqualTo("initialized-by-parent");
        Assertions.assertThat(secondInstance.parentState).isEqualTo("initialized-by-parent");
        Assertions.assertThat(firstInstance.childState).isNull();
        Assertions.assertThat(secondInstance.childState).isNull();
    }

    static class NonSerializableParent {
        static final AtomicInteger constructorCalls = new AtomicInteger();

        String parentState;

        NonSerializableParent() {
            constructorCalls.incrementAndGet();
            this.parentState = "initialized-by-parent";
        }
    }

    static class SerializableChild extends NonSerializableParent implements Serializable {
        private static final long serialVersionUID = 1L;

        static final AtomicInteger constructorCalls = new AtomicInteger();

        String childState;

        SerializableChild() {
            constructorCalls.incrementAndGet();
            this.childState = "initialized-by-child";
        }
    }
}
