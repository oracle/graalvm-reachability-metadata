/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_objenesis.objenesis;

import java.io.Serializable;

import org.junit.jupiter.api.Test;
import org.objenesis.instantiator.sun.SunReflectionFactoryInstantiator;
import org.objenesis.instantiator.sun.SunReflectionFactorySerializationInstantiator;

import static org.assertj.core.api.Assertions.assertThat;

class SunReflectionFactoryHelperTest {

    @Test
    void sunReflectionFactoryInstantiatorCreatesObjectWithoutInvokingConstructor() {
        ConstructorTrackedType.reset();

        SunReflectionFactoryInstantiator<ConstructorTrackedType> instantiator =
            new SunReflectionFactoryInstantiator<>(ConstructorTrackedType.class);

        ConstructorTrackedType instance = instantiator.newInstance();

        assertThat(instance).isNotNull();
        assertThat(ConstructorTrackedType.constructorCalls).isZero();
        assertThat(instance.constructorAssignedValue).isNull();
    }

    @Test
    void sunReflectionFactorySerializationInstantiatorUsesSerializableConstructionRules() {
        NonSerializableBase.reset();
        SerializableChild.reset();

        SunReflectionFactorySerializationInstantiator<SerializableChild> instantiator =
            new SunReflectionFactorySerializationInstantiator<>(SerializableChild.class);

        SerializableChild instance = instantiator.newInstance();

        assertThat(instance).isNotNull();
        assertThat(NonSerializableBase.constructorCalls).isEqualTo(1);
        assertThat(SerializableChild.constructorCalls).isZero();
        assertThat(instance.baseState).isEqualTo("base constructor invoked");
        assertThat(instance.childState).isNull();
    }

    static final class ConstructorTrackedType {
        static int constructorCalls;

        String constructorAssignedValue;

        ConstructorTrackedType() {
            constructorCalls++;
            constructorAssignedValue = "constructed";
        }

        static void reset() {
            constructorCalls = 0;
        }
    }

    static class NonSerializableBase {
        static int constructorCalls;

        String baseState;

        NonSerializableBase() {
            constructorCalls++;
            baseState = "base constructor invoked";
        }

        static void reset() {
            constructorCalls = 0;
        }
    }

    static final class SerializableChild extends NonSerializableBase implements Serializable {
        private static final long serialVersionUID = 1L;

        static int constructorCalls;

        String childState;

        SerializableChild() {
            constructorCalls++;
            childState = "child constructor invoked";
        }

        static void reset() {
            constructorCalls = 0;
        }
    }
}
