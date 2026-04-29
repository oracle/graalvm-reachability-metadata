/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_modelmapper.modelmapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.modelmapper.internal.objenesis.instantiator.sun.SunReflectionFactorySerializationInstantiator;

public class SunReflectionFactorySerializationInstantiatorTest {
    @Test
    void createsSerializableInstanceUsingNonSerializableParentConstructor() {
        NonSerializableParent.constructorCalls.set(0);
        SerializableChild.constructorCalls.set(0);

        SunReflectionFactorySerializationInstantiator<SerializableChild> instantiator =
            new SunReflectionFactorySerializationInstantiator<>(SerializableChild.class);
        SerializableChild instance = instantiator.newInstance();

        assertThat(instance).isNotNull();
        assertThat(instance).isExactlyInstanceOf(SerializableChild.class);
        assertThat(NonSerializableParent.constructorCalls).hasValue(1);
        assertThat(SerializableChild.constructorCalls).hasValue(0);
        assertThat(instance.parentState).isEqualTo("initialized-by-parent");
        assertThat(instance.childState).isNull();
    }

    public static class NonSerializableParent {
        static final AtomicInteger constructorCalls = new AtomicInteger();

        String parentState;

        public NonSerializableParent() {
            constructorCalls.incrementAndGet();
            this.parentState = "initialized-by-parent";
        }
    }

    public static class SerializableChild extends NonSerializableParent implements Serializable {
        private static final long serialVersionUID = 1L;

        static final AtomicInteger constructorCalls = new AtomicInteger();

        String childState;

        public SerializableChild() {
            constructorCalls.incrementAndGet();
            this.childState = "initialized-by-child";
        }
    }
}
