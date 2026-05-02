/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjrt;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import org.aspectj.lang.reflect.ConstructorSignature;
import org.aspectj.runtime.reflect.Factory;
import org.junit.jupiter.api.Test;

public class ConstructorSignatureImplTest {
    @Test
    void resolvesDeclaredConstructorOnDeclaringType() {
        Factory factory = new Factory("ConstructorSignatureImplTest.java", ConstructorSignatureImplTest.class);
        ConstructorSignature signature = factory.makeConstructorSig(
                Modifier.PUBLIC,
                ConstructedService.class,
                new Class[] {String.class, int.class},
                new String[] {"name", "priority"},
                new Class[] {IllegalArgumentException.class});

        Constructor constructor = signature.getConstructor();

        assertThat(constructor).isNotNull();
        assertThat(constructor.getDeclaringClass()).isEqualTo(ConstructedService.class);
        assertThat(constructor.getParameterTypes()).containsExactly(String.class, int.class);
        assertThat(constructor.getExceptionTypes()).containsExactly(IllegalArgumentException.class);
    }

    public static class ConstructedService {
        public ConstructedService(String name, int priority) throws IllegalArgumentException {
            if (name == null || priority < 0) {
                throw new IllegalArgumentException("name must be set and priority must be non-negative");
            }
        }
    }
}
