/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.objenesis.instantiator.sun.SunReflectionFactorySerializationInstantiator;

/** Verifies Java-serialization constructor semantics through ReflectionFactory. */
public class SunReflectionFactorySerializationInstantiatorTest {
    @Test
    void initializesNonSerializableParentButBypassesSerializableChild() {
        SunReflectionFactorySerializationInstantiator<SerializableChild> instantiator =
                new SunReflectionFactorySerializationInstantiator<>(SerializableChild.class);

        SerializableChild instance = instantiator.newInstance();

        assertThat(instance.parentValue).isEqualTo("parent");
        assertThat(instance.childValue).isNull();
    }

    public static class Parent {
        protected final String parentValue;

        public Parent() {
            parentValue = "parent";
        }
    }

    public static final class SerializableChild extends Parent implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
        private final String childValue;

        public SerializableChild() {
            childValue = "child";
        }
    }
}
