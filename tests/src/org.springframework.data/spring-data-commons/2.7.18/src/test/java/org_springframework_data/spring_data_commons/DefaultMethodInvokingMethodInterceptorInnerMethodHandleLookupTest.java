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
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.util.ReflectionUtils;

public class DefaultMethodInvokingMethodInterceptorInnerMethodHandleLookupTest {

    @Test
    void invokesDefaultProjectionMethodThroughMethodHandleLookup() {
        SpelAwareProxyProjectionFactory factory = new SpelAwareProxyProjectionFactory();
        PersonProjection projection = factory.createProjection(PersonProjection.class,
                Map.of("firstName", "Ada", "lastName", "Lovelace"));

        assertThat(projection.getDisplayName()).isEqualTo("Ada Lovelace");
    }

    @Test
    void resolvesStaticInterfaceMethodThroughMethodHandleLookup() throws Throwable {
        Method method = StaticLookupProjection.class.getMethod("greeting", String.class);
        MethodHandle handle = lookup(method);

        Object result = handle.invokeWithArguments("Spring Data");

        assertThat(result).isEqualTo("Hello Spring Data");
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

    public interface PersonProjection {

        String getFirstName();

        String getLastName();

        default String getDisplayName() {
            return getFirstName() + " " + getLastName();
        }
    }

    public interface StaticLookupProjection {

        static String greeting(String name) {
            return "Hello " + name;
        }
    }
}
