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
import org.springframework.objenesis.instantiator.basic.ObjectStreamClassInstantiator;

/** Verifies Objenesis construction rules for serializable classes. */
public class ObjectStreamClassInstantiatorTest {
    @Test
    void createsSerializableInstanceWithoutCallingItsConstructor() {
        SerializableValue.constructorCalls.set(0);
        SerializableParent.constructorCalls.set(0);
        ObjectStreamClassInstantiator<SerializableValue> instantiator =
                new ObjectStreamClassInstantiator<>(SerializableValue.class);

        SerializableValue value = instantiator.newInstance();

        assertThat(SerializableParent.constructorCalls).hasValue(1);
        assertThat(SerializableValue.constructorCalls).hasValue(0);
        assertThat(value.getParentState()).isEqualTo("initialized by parent");
        assertThat(value.getValueState()).isNull();
    }

    public static class SerializableParent {
        private static final AtomicInteger constructorCalls = new AtomicInteger();

        private final String parentState;

        public SerializableParent() {
            constructorCalls.incrementAndGet();
            this.parentState = "initialized by parent";
        }

        public String getParentState() {
            return this.parentState;
        }
    }

    public static final class SerializableValue extends SerializableParent implements Serializable {
        private static final long serialVersionUID = 1L;
        private static final AtomicInteger constructorCalls = new AtomicInteger();

        private final String valueState;

        public SerializableValue() {
            constructorCalls.incrementAndGet();
            this.valueState = "initialized by child";
        }

        public String getValueState() {
            return this.valueState;
        }
    }
}
