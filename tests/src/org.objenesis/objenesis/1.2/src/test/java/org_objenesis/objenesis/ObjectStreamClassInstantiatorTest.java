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
import org.objenesis.instantiator.basic.ObjectStreamClassInstantiator;

public class ObjectStreamClassInstantiatorTest {

    @Test
    void createsSerializableInstancesWithoutInvokingTargetConstructor() {
        NonSerializableParent.constructorCalls.set(0);
        SerializableChild.constructorCalls.set(0);

        ObjectStreamClassInstantiator instantiator =
            new ObjectStreamClassInstantiator(SerializableChild.class);
        Object instance = instantiator.newInstance();

        Assertions.assertThat(instance).isInstanceOf(SerializableChild.class);
        SerializableChild child = (SerializableChild) instance;
        Assertions.assertThat(child.parentState).isEqualTo("parent-constructed");
        Assertions.assertThat(child.childState).isNull();
        Assertions.assertThat(NonSerializableParent.constructorCalls).hasValue(1);
        Assertions.assertThat(SerializableChild.constructorCalls).hasValue(0);
    }

    public static class NonSerializableParent {
        static final AtomicInteger constructorCalls = new AtomicInteger();

        String parentState;

        public NonSerializableParent() {
            constructorCalls.incrementAndGet();
            this.parentState = "parent-constructed";
        }
    }

    public static class SerializableChild extends NonSerializableParent implements Serializable {
        private static final long serialVersionUID = 1L;

        static final AtomicInteger constructorCalls = new AtomicInteger();

        String childState;

        public SerializableChild() {
            constructorCalls.incrementAndGet();
            this.childState = "child-constructed";
        }
    }
}
