/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_lettuce.lettuce_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

public class DefaultMethodsInnerMethodHandleLookupAnonymous2Test {

    @Test
    void encapsulatedLookupInvokesDefaultInterfaceMethod() throws Throwable {
        Method method = Calculations.class.getMethod("doubleValue", int.class);

        MethodHandle methodHandle = invokeEncapsulatedLookup(method);
        Object result = methodHandle.bindTo(new Calculator()).invokeWithArguments(21);

        assertThat(result).isEqualTo(42);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static MethodHandle invokeEncapsulatedLookup(Method method) throws Throwable {
        Class<?> lookupStrategyClass = Class.forName("io.lettuce.core.internal.DefaultMethods$MethodHandleLookup");
        Enum<?> encapsulatedLookup = Enum.valueOf((Class<? extends Enum>) lookupStrategyClass, "ENCAPSULATED");
        Class<?> concreteLookupClass = encapsulatedLookup.getClass();
        MethodHandle lookup = MethodHandles.privateLookupIn(concreteLookupClass, MethodHandles.lookup())
                .findVirtual(concreteLookupClass, "lookup", MethodType.methodType(MethodHandle.class, Method.class));

        return (MethodHandle) lookup.invoke(encapsulatedLookup, method);
    }

    public interface Calculations {

        default int doubleValue(int value) {
            return value * 2;
        }
    }

    public static final class Calculator implements Calculations {
    }
}
