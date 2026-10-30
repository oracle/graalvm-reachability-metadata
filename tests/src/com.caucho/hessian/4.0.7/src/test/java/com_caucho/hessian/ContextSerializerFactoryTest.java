/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_caucho.hessian;

import com.caucho.hessian.io.AbstractStringValueDeserializer;
import com.caucho.hessian.io.SerializerFactory;
import com.caucho.hessian.io.StringValueSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

public class ContextSerializerFactoryTest {
    @Test
    void discoversConventionNamedCodecs() throws Exception {
        for (boolean hessian2 : new boolean[] {false, true}) {
            Token original = new Token("signed-token");
            Token restored = HessianStreams.roundTrip(original, new SerializerFactory(), hessian2);
            assertNotSame(original, restored);
            assertEquals("signed-token", restored.value);
        }
    }

    @Test
    void discoversCodecsRegisteredInClasspathResources() throws Exception {
        for (boolean hessian2 : new boolean[] {false, true}) {
            RegisteredToken original = new RegisteredToken("resource-token");
            RegisteredToken restored = HessianStreams.roundTrip(original, new SerializerFactory(), hessian2);
            assertNotSame(original, restored);
            assertEquals("resource-token", restored.value);
        }
    }

    public static class Token {
        private final String value;

        public Token(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return value;
        }
    }

    public static class TokenHessianSerializer extends StringValueSerializer {
    }

    public static class TokenHessianDeserializer extends AbstractStringValueDeserializer {
        @Override
        public Class<?> getType() {
            return Token.class;
        }

        @Override
        protected Object create(String value) {
            return new Token(value);
        }
    }

    public static class RegisteredToken {
        private final String value;

        public RegisteredToken(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return value;
        }
    }

    public static class RegisteredTokenSerializer extends StringValueSerializer {
    }

    public static class RegisteredTokenDeserializer extends AbstractStringValueDeserializer {
        @Override
        public Class<?> getType() {
            return RegisteredToken.class;
        }

        @Override
        protected Object create(String value) {
            return new RegisteredToken(value);
        }
    }
}
