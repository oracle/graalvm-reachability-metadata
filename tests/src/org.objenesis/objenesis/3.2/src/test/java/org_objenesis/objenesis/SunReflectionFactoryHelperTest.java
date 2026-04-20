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
import org.objenesis.ObjenesisSerializer;
import org.objenesis.ObjenesisStd;

public class SunReflectionFactoryHelperTest {

    @Test
    void createsInstancesWithoutRunningTheTargetConstructor() {
        ConstructorBypassedType.constructorCalls.set(0);

        ConstructorBypassedType instance = new ObjenesisStd().newInstance(ConstructorBypassedType.class);

        Assertions.assertThat(instance).isNotNull();
        Assertions.assertThat(ConstructorBypassedType.constructorCalls).hasValue(0);
        Assertions.assertThat(instance.message).isNull();
        Assertions.assertThat(instance.number).isZero();
    }

    @Test
    void followsSerializationConstructionRulesForSerializableTypes() {
        NonSerializableParent.constructorCalls.set(0);
        SerializableChild.constructorCalls.set(0);

        SerializableChild instance = new ObjenesisSerializer().newInstance(SerializableChild.class);

        Assertions.assertThat(instance).isNotNull();
        Assertions.assertThat(NonSerializableParent.constructorCalls).hasValue(1);
        Assertions.assertThat(SerializableChild.constructorCalls).hasValue(0);
        Assertions.assertThat(instance.parentState).isEqualTo("initialized-by-parent");
        Assertions.assertThat(instance.childState).isNull();
    }

    static class ConstructorBypassedType {
        static final AtomicInteger constructorCalls = new AtomicInteger();

        final String message;
        final int number;

        ConstructorBypassedType() {
            constructorCalls.incrementAndGet();
            this.message = "constructed";
            this.number = 42;
        }
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
