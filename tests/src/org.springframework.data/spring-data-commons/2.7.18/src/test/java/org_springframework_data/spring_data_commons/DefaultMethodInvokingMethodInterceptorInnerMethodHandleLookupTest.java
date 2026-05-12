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

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.util.ReflectionUtils;

public class DefaultMethodInvokingMethodInterceptorInnerMethodHandleLookupTest {

    @Test
    void invokesDefaultLibraryMethodThroughMethodHandleLookup() throws Throwable {
        Method method = Pageable.class.getMethod("isPaged");
        MethodHandle handle = lookup(method);

        Object result = handle.bindTo(PageRequest.of(0, 1)).invokeWithArguments();

        assertThat(result).isEqualTo(true);
    }

    @Test
    void resolvesStaticInterfaceMethodThroughMethodHandleLookup() throws Throwable {
        Method method = Pageable.class.getMethod("unpaged");
        MethodHandle handle = lookup(method);

        Object result = handle.invokeWithArguments();

        assertThat(result).isInstanceOf(Pageable.class);
        assertThat(((Pageable) result).isUnpaged()).isTrue();
    }

    private static MethodHandle lookup(Method method) throws Throwable {
        Class<?> lookupType = Class.forName(
                "org.springframework.data.projection.DefaultMethodInvokingMethodInterceptor$MethodHandleLookup");
        Method getMethodHandleLookup = lookupType.getDeclaredMethod("getMethodHandleLookup");
        Method lookup = lookupType.getDeclaredMethod("lookup", Method.class);
        ReflectionUtils.makeAccessible(getMethodHandleLookup);
        ReflectionUtils.makeAccessible(lookup);

        Object lookupStrategy = getMethodHandleLookup.invoke(null);

        try {
            return (MethodHandle) lookup.invoke(lookupStrategy, method);
        } catch (InvocationTargetException ex) {
            throw ex.getTargetException();
        }
    }
}
