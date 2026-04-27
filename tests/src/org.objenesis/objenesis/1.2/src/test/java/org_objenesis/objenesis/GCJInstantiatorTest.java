/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_objenesis.objenesis;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.objenesis.instantiator.gcj.GCJInstantiator;
import org.objenesis.instantiator.gcj.GCJInstantiatorBase;

public class GCJInstantiatorTest {

    @Test
    void delegatesInstanceCreationToConfiguredGcjNewObjectHook() throws Exception {
        ConstructorBypassedType preparedInstance = new ConstructorBypassedType();
        ConstructorBypassedType.constructorCalls.set(0);
        Method newObjectMethod = GCJObjectInputStreamSupport.class.getDeclaredMethod(
            "newObject",
            Class.class,
            Class.class
        );

        GCJStateAccess.configure(
            newObjectMethod,
            new GCJObjectInputStreamSupport(preparedInstance)
        );

        GCJInstantiator instantiator = new GCJInstantiator(ConstructorBypassedType.class);
        Object instance = instantiator.newInstance();

        Assertions.assertThat(instance).isSameAs(preparedInstance);
        Assertions.assertThat(ConstructorBypassedType.constructorCalls).hasValue(0);
    }

    public static class ConstructorBypassedType {
        static final AtomicInteger constructorCalls = new AtomicInteger();

        public ConstructorBypassedType() {
            constructorCalls.incrementAndGet();
        }
    }

    public static class GCJObjectInputStreamSupport extends ObjectInputStream {
        private final Object preparedInstance;

        public GCJObjectInputStreamSupport(Object preparedInstance) throws IOException {
            super();
            this.preparedInstance = preparedInstance;
        }

        public Object newObject(Class<?> type, Class<?> parentType) {
            if (type != ConstructorBypassedType.class) {
                throw new IllegalArgumentException("Unexpected type: " + type.getName());
            }
            if (parentType != Object.class) {
                throw new IllegalArgumentException(
                    "Unexpected parent type: " + parentType.getName()
                );
            }
            return preparedInstance;
        }
    }

    private abstract static class GCJStateAccess extends GCJInstantiatorBase {
        private GCJStateAccess(Class<?> type) {
            super(type);
        }

        static void configure(Method method, ObjectInputStream stream) {
            newObjectMethod = method;
            dummyStream = stream;
        }
    }
}
