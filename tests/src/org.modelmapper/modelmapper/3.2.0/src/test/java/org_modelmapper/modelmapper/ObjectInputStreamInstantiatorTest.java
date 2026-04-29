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
import org.modelmapper.internal.objenesis.instantiator.basic.ObjectInputStreamInstantiator;

public class ObjectInputStreamInstantiatorTest {
    @Test
    void createsSerializableInstancesByReadingGeneratedObjectStream() {
        NonSerializableParent.constructorCalls.set(0);
        SerializableChild.constructorCalls.set(0);

        ObjectInputStreamInstantiator<SerializableChild> instantiator =
            new ObjectInputStreamInstantiator<>(SerializableChild.class);
        SerializableChild firstInstance = instantiator.newInstance();
        SerializableChild secondInstance = instantiator.newInstance();

        assertThat(firstInstance).isNotNull();
        assertThat(secondInstance).isNotNull();
        assertThat(secondInstance).isNotSameAs(firstInstance);
        assertThat(firstInstance).isExactlyInstanceOf(SerializableChild.class);
        assertThat(secondInstance).isExactlyInstanceOf(SerializableChild.class);
        assertThat(NonSerializableParent.constructorCalls).hasValue(2);
        assertThat(SerializableChild.constructorCalls).hasValue(0);
        assertThat(firstInstance.parentState).isEqualTo("initialized-by-parent");
        assertThat(secondInstance.parentState).isEqualTo("initialized-by-parent");
        assertThat(firstInstance.childState).isNull();
        assertThat(secondInstance.childState).isNull();
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
