/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.Serializable;

import org.junit.jupiter.api.Test;
import org.springframework.objenesis.instantiator.sun.SunReflectionFactorySerializationInstantiator;

/** Verifies Java serialization construction semantics through the reflection factory. */
public class SunReflectionFactorySerializationInstantiatorTest {
    @Test
    void invokesNonSerializableParentConstructorOnly() {
        SunReflectionFactorySerializationInstantiator<SerializableChild> instantiator =
                new SunReflectionFactorySerializationInstantiator<>(SerializableChild.class);

        SerializableChild child = instantiator.newInstance();

        assertThat(child.parentValue).isEqualTo("parent");
        assertThat(child.childValue).isNull();
    }

    public static class NonSerializableParent {
        protected final String parentValue;

        public NonSerializableParent() {
            this.parentValue = "parent";
        }
    }

    public static final class SerializableChild extends NonSerializableParent implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String childValue;

        public SerializableChild() {
            this.childValue = "child";
        }
    }
}
