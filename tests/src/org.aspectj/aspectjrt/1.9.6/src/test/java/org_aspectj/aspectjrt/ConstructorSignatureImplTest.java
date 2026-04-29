/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjrt;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import org.aspectj.lang.reflect.ConstructorSignature;
import org.aspectj.runtime.reflect.Factory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ConstructorSignatureImplTest {

    @Test
    void resolvesDeclaredConstructorFromSignature() {
        ConstructorSignature signature = createSignature(
                ConstructedComponent.class,
                new Class<?>[] {String.class, int.class},
                new String[] {"name", "count"},
                new Class<?>[] {IllegalArgumentException.class}
        );

        Constructor<?> constructor = signature.getConstructor();

        assertThat(constructor).isNotNull();
        assertThat(constructor.getDeclaringClass()).isEqualTo(ConstructedComponent.class);
        assertThat(constructor.getModifiers()).isEqualTo(Modifier.PUBLIC);
        assertThat(constructor.getParameterTypes()).containsExactly(String.class, int.class);
        assertThat(constructor.getExceptionTypes()).containsExactly(IllegalArgumentException.class);
    }

    private static ConstructorSignature createSignature(
            Class<?> declaringType,
            Class<?>[] parameterTypes,
            String[] parameterNames,
            Class<?>[] exceptionTypes) {
        Factory factory = new Factory("ConstructorSignatureImplTest.java", ConstructorSignatureImplTest.class);
        return factory.makeConstructorSig(
                Modifier.PUBLIC,
                declaringType,
                parameterTypes,
                parameterNames,
                exceptionTypes
        );
    }

    public static class ConstructedComponent {
        private final String name;
        private final int count;

        public ConstructedComponent(String name, int count) throws IllegalArgumentException {
            if (name.isEmpty()) {
                throw new IllegalArgumentException("name must not be empty");
            }
            this.name = name;
            this.count = count;
        }

        public String description() {
            return name + ':' + count;
        }
    }
}
