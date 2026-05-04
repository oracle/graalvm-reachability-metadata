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
    void resolvesConstructorDeclaredOnSignatureDeclaringType() {
        Factory factory = new Factory("ConstructorSignatureImplTest.java", ConstructorSignatureImplTest.class);
        ConstructorSignature signature = factory.makeConstructorSig(
                Modifier.PUBLIC,
                ConstructorSubject.class,
                new Class[] {String.class, int.class},
                new String[] {"name", "count"},
                new Class[] {IllegalArgumentException.class});

        Constructor<?> constructor = signature.getConstructor();

        assertThat(constructor).isNotNull();
        assertThat(constructor.getDeclaringClass()).isEqualTo(ConstructorSubject.class);
        assertThat(constructor.getParameterTypes()).containsExactly(String.class, int.class);
        assertThat(constructor.getExceptionTypes()).containsExactly(IllegalArgumentException.class);
    }

    public static class ConstructorSubject {
        public ConstructorSubject(String name, int count) throws IllegalArgumentException {
            if (name == null || count < 0) {
                throw new IllegalArgumentException("Invalid constructor arguments");
            }
        }
    }
}
