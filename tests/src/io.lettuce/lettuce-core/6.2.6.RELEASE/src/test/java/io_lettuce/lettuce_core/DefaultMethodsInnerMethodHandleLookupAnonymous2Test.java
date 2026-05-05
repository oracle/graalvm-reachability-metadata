/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_lettuce.lettuce_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

public class DefaultMethodsInnerMethodHandleLookupAnonymous2Test {

    @Test
    void encapsulatedLookupFindsSpecialHandleForDefaultInterfaceMethod() throws Throwable {
        Method defaultMethod = Phrase.class.getMethod("format", String.class);
        assertThat(defaultMethod.isDefault()).isTrue();

        Object encapsulatedLookup = methodHandleLookup("ENCAPSULATED");
        Method lookupMethod = encapsulatedLookup.getClass().getSuperclass().getDeclaredMethod("lookup", Method.class);
        lookupMethod.setAccessible(true);

        MethodHandle methodHandle = (MethodHandle) invokeLookup(lookupMethod, encapsulatedLookup, defaultMethod);
        Object result = methodHandle.bindTo(new PhraseImpl("world")).invokeWithArguments("hello");

        assertThat(result).isEqualTo("hello world");
    }

    private static Object methodHandleLookup(String name) throws ClassNotFoundException {
        Class<?> lookupType = Class.forName("io.lettuce.core.internal.DefaultMethods$MethodHandleLookup");
        for (Object constant : lookupType.getEnumConstants()) {
            if (((Enum<?>) constant).name().equals(name)) {
                return constant;
            }
        }
        throw new AssertionError("Missing method handle lookup: " + name);
    }

    private static Object invokeLookup(Method lookupMethod, Object target, Method defaultMethod) throws Throwable {
        try {
            return lookupMethod.invoke(target, defaultMethod);
        } catch (InvocationTargetException e) {
            throw e.getTargetException();
        }
    }

    public interface Phrase {

        default String format(String prefix) {
            return prefix + " " + value();
        }

        String value();
    }

    public static final class PhraseImpl implements Phrase {

        private final String value;

        PhraseImpl(String value) {
            this.value = value;
        }

        @Override
        public String value() {
            return value;
        }
    }
}
