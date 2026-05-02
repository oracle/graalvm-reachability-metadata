/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodIntrospector;

public class MethodIntrospectorTest {

    @Test
    void selectsInvocableInterfaceMethodForProxyLikeTarget() {
        Method implementationMethod = selectMethod(GreetingImplementation.class, "greet");

        Method invocableMethod = MethodIntrospector.selectInvocableMethod(
                implementationMethod,
                GreetingProxy.class
        );

        assertThat(invocableMethod.getDeclaringClass()).isEqualTo(GreetingContract.class);
        assertThat(invocableMethod.getName()).isEqualTo("greet");
        assertThat(invocableMethod.getParameterTypes()).containsExactly(String.class);
    }

    @Test
    void selectsInvocableMethodDeclaredOnTargetTypeWhenInterfacesDoNotMatch() {
        Method sourceMethod = selectMethod(SourceEndpoint.class, "handle");

        Method invocableMethod = MethodIntrospector.selectInvocableMethod(
                sourceMethod,
                TargetEndpoint.class
        );

        assertThat(invocableMethod.getDeclaringClass()).isEqualTo(TargetEndpoint.class);
        assertThat(invocableMethod.getName()).isEqualTo("handle");
        assertThat(invocableMethod.getParameterTypes()).containsExactly(Integer.class);
    }

    private static Method selectMethod(Class<?> targetType, String methodName) {
        MethodIntrospector.MetadataLookup<Method> metadataLookup = method ->
                method.getName().equals(methodName) ? method : null;
        Map<Method, Method> selectedMethods = MethodIntrospector.selectMethods(targetType, metadataLookup);

        assertThat(selectedMethods).hasSize(1);
        return selectedMethods.keySet().iterator().next();
    }

    interface GreetingContract {
        String greet(String name);
    }

    static class GreetingImplementation implements GreetingContract {
        @Override
        public String greet(String name) {
            return "hello " + name;
        }
    }

    static class GreetingProxy implements GreetingContract {
        @Override
        public String greet(String name) {
            return "proxy " + name;
        }
    }

    static class SourceEndpoint {
        public String handle(Integer value) {
            return "source " + value;
        }
    }

    static class TargetEndpoint {
        public String handle(Integer value) {
            return "target " + value;
        }
    }
}
