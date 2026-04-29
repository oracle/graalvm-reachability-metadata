/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjrt;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.aspectj.lang.reflect.MethodSignature;
import org.aspectj.runtime.reflect.Factory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MethodSignatureImplTest {

    @Test
    void resolvesMethodDeclaredDirectlyOnSignatureType() {
        MethodSignature signature = createSignature(
                "directCall",
                DirectMethodOwner.class,
                new Class<?>[] {int.class},
                String.class
        );

        Method method = signature.getMethod();

        assertThat(method).isNotNull();
        assertThat(method.getDeclaringClass()).isEqualTo(DirectMethodOwner.class);
        assertThat(method.getName()).isEqualTo("directCall");
        assertThat(method.getReturnType()).isEqualTo(String.class);
        assertThat(method.getParameterTypes()).containsExactly(int.class);
    }

    @Test
    void searchesInterfacesWhenMethodIsInheritedBySignatureType() {
        MethodSignature signature = createSignature(
                "inheritedCall",
                AbstractInheritedMethodOwner.class,
                new Class<?>[] {String.class},
                boolean.class
        );

        Method method = signature.getMethod();

        assertThat(method).isNotNull();
        assertThat(method.getDeclaringClass()).isEqualTo(InheritedContract.class);
        assertThat(method.getName()).isEqualTo("inheritedCall");
        assertThat(method.getReturnType()).isEqualTo(boolean.class);
        assertThat(method.getParameterTypes()).containsExactly(String.class);
    }

    private static MethodSignature createSignature(
            String name,
            Class<?> declaringType,
            Class<?>[] parameterTypes,
            Class<?> returnType) {
        Factory factory = new Factory("MethodSignatureImplTest.java", MethodSignatureImplTest.class);
        return factory.makeMethodSig(
                Modifier.PUBLIC,
                name,
                declaringType,
                parameterTypes,
                new String[] {"value"},
                new Class<?>[0],
                returnType
        );
    }

    public static class DirectMethodOwner {
        public String directCall(int value) {
            return Integer.toString(value);
        }
    }

    public abstract static class AbstractInheritedMethodOwner implements InheritedContract {
    }

    public interface InheritedContract {
        boolean inheritedCall(String value);
    }
}
