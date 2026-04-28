/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thoughtworks_xstream.xstream;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicInteger;

import com.thoughtworks.xstream.converters.reflection.PureJavaReflectionProvider;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PureJavaReflectionProviderTest {
    @Test
    void createsObjectsWithNoArgumentConstructors() {
        PureJavaReflectionProvider provider = new PureJavaReflectionProvider();

        Object created = provider.newInstance(ConstructorCreatedValue.class);

        assertThat(created).isExactlyInstanceOf(ConstructorCreatedValue.class);
        assertThat(((ConstructorCreatedValue)created).value).isEqualTo("created by constructor");
    }

    @Test
    void createsSerializableObjectsWithoutCallingTheirConstructors() {
        PureJavaReflectionProvider provider = new PureJavaReflectionProvider();
        SerializationCreatedValue.constructorCalls.set(0);

        Object created = provider.newInstance(SerializationCreatedValue.class);

        assertThat(created).isExactlyInstanceOf(SerializationCreatedValue.class);
        assertThat(SerializationCreatedValue.constructorCalls).hasValue(0);
        assertThat(((SerializationCreatedValue)created).value).isNull();
    }

    public static final class ConstructorCreatedValue {
        private final String value;

        private ConstructorCreatedValue() {
            value = "created by constructor";
        }
    }

    public static final class SerializationCreatedValue implements Serializable {
        private static final long serialVersionUID = 1L;
        private static final AtomicInteger constructorCalls = new AtomicInteger();

        private final String value;

        public SerializationCreatedValue(String value) {
            constructorCalls.incrementAndGet();
            this.value = value;
        }
    }
}
