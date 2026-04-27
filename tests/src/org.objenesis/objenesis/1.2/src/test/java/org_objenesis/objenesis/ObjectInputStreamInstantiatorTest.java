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
import org.objenesis.instantiator.basic.ObjectInputStreamInstantiator;

public class ObjectInputStreamInstantiatorTest {

    @Test
    void createsSerializableInstancesFromObjectInputStreamWithoutCallingTargetConstructor() {
        SerializableTarget.constructorCalls.set(0);

        ObjectInputStreamInstantiator instantiator =
            new ObjectInputStreamInstantiator(SerializableTarget.class);
        Object firstInstance = instantiator.newInstance();
        Object secondInstance = instantiator.newInstance();

        Assertions.assertThat(firstInstance).isInstanceOf(SerializableTarget.class);
        Assertions.assertThat(secondInstance).isInstanceOf(SerializableTarget.class);
        Assertions.assertThat(secondInstance).isNotSameAs(firstInstance);

        SerializableTarget firstTarget = (SerializableTarget) firstInstance;
        SerializableTarget secondTarget = (SerializableTarget) secondInstance;
        Assertions.assertThat(firstTarget.message).isNull();
        Assertions.assertThat(secondTarget.message).isNull();
        Assertions.assertThat(SerializableTarget.constructorCalls).hasValue(0);
    }

    public static class SerializableTarget implements Serializable {
        private static final long serialVersionUID = 1L;

        static final AtomicInteger constructorCalls = new AtomicInteger();

        String message;

        public SerializableTarget() {
            constructorCalls.incrementAndGet();
            this.message = "constructed";
        }
    }
}
