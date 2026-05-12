/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_data.spring_data_commons;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.util.ReflectionUtils;

public class DefaultMethodInvokingMethodInterceptorInnerMethodHandleLookupAnonymous2Test {

    @Test
    void openLookupInvokesLibraryDefaultMethod() throws Throwable {
        Method method = Pageable.class.getMethod("isPaged");
        MethodHandle methodHandle = lookupWithOpenStrategy(method);

        Object result = methodHandle.bindTo(PageRequest.of(0, 1)).invokeWithArguments();

        assertThat(result).isEqualTo(true);
    }

    private static MethodHandle lookupWithOpenStrategy(Method method) throws Throwable {
        Class<?> lookupType = Class.forName(
                "org.springframework.data.projection.DefaultMethodInvokingMethodInterceptor$MethodHandleLookup");
        Object openLookup = Arrays.stream(lookupType.getEnumConstants())
                .filter(constant -> ((Enum<?>) constant).name().equals("OPEN"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("OPEN lookup strategy not found"));
        Method lookup = lookupType.getDeclaredMethod("lookup", Method.class);
        ReflectionUtils.makeAccessible(lookup);

        try {
            return (MethodHandle) lookup.invoke(openLookup, method);
        } catch (InvocationTargetException ex) {
            throw ex.getTargetException();
        }
    }
}
