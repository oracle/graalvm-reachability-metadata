/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_caucho.hessian;

import com.caucho.hessian.io.JavaDeserializer;
import com.caucho.hessian.io.SerializerFactory;
import org.junit.jupiter.api.Test;

import java.io.Serializable;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JavaDeserializerTest {
    @Test
    void restoresPrivateInheritedFieldsAndResolvesValue() throws Exception {
        Payload original = new Payload(42);
        original.message = "priority";
        for (boolean hessian2 : new boolean[] {false, true}) {
            SerializerFactory factory = HessianStreams.codecs(Payload.class, null, new JavaDeserializer(Payload.class));
            assertEquals("priority:0", HessianStreams.roundTrip((Object) original, factory, hessian2));
        }
    }

    public static class Parent implements Serializable {
        protected String message;
    }

    public static class Payload extends Parent {
        private transient int constructorArgument;

        private Payload(int constructorArgument) {
            this.constructorArgument = constructorArgument;
        }

        public Object readResolve() {
            return message + ":" + constructorArgument;
        }
    }
}
