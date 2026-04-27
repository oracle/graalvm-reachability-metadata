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
import org.objenesis.instantiator.sun.SunReflectionFactorySerializationInstantiator;

public class SunReflectionFactorySerializationInstantiatorTest {

    @Test
    void createsSerializableInstancesByRunningOnlyTheFirstNonSerializableSuperclassConstructor() {
        NonSerializableParent.constructorCalls.set(0);
        SerializableMiddle.constructorCalls.set(0);
        SerializableChild.constructorCalls.set(0);

        SunReflectionFactorySerializationInstantiator instantiator =
            new SunReflectionFactorySerializationInstantiator(SerializableChild.class);
        Object instance = instantiator.newInstance();

        Assertions.assertThat(instance).isInstanceOf(SerializableChild.class);
        SerializableChild child = (SerializableChild) instance;
        Assertions.assertThat(NonSerializableParent.constructorCalls).hasValue(1);
        Assertions.assertThat(SerializableMiddle.constructorCalls).hasValue(0);
        Assertions.assertThat(SerializableChild.constructorCalls).hasValue(0);
        Assertions.assertThat(child.parentValue).isEqualTo("parent-constructor");
        Assertions.assertThat(child.middleValue).isNull();
        Assertions.assertThat(child.childValue).isNull();
        Assertions.assertThat(child.childNumber).isZero();
    }

    public static class NonSerializableParent {
        static final AtomicInteger constructorCalls = new AtomicInteger();

        final String parentValue;

        public NonSerializableParent() {
            constructorCalls.incrementAndGet();
            this.parentValue = "parent-constructor";
        }
    }

    public static class SerializableMiddle extends NonSerializableParent implements Serializable {
        private static final long serialVersionUID = 1L;

        static final AtomicInteger constructorCalls = new AtomicInteger();

        final String middleValue;

        public SerializableMiddle() {
            constructorCalls.incrementAndGet();
            this.middleValue = "middle-constructor";
        }
    }

    public static class SerializableChild extends SerializableMiddle {
        private static final long serialVersionUID = 1L;

        static final AtomicInteger constructorCalls = new AtomicInteger();

        final String childValue;
        final int childNumber;

        public SerializableChild() {
            constructorCalls.incrementAndGet();
            this.childValue = "child-constructor";
            this.childNumber = 42;
        }
    }
}
