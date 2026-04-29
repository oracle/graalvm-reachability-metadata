/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjweaver;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import org.aspectj.lang.reflect.ConstructorSignature;
import org.aspectj.runtime.reflect.Factory;
import org.junit.jupiter.api.Test;

public class ConstructorSignatureImplTest {
    @Test
    void resolvesDeclaredConstructorFromSignature() {
        Factory factory = new Factory("ConstructorSignatureImplTest.java", ConstructorSignatureImplTest.class);
        ConstructorSignature signature = factory.makeConstructorSig(Modifier.PUBLIC, Factory.class,
                new Class<?>[] {String.class, Class.class}, new String[] {"filename", "lexicalClass"}, new Class<?>[0]);

        Constructor<?> constructor = signature.getConstructor();

        assertThat(constructor).isNotNull();
        assertThat(constructor.getDeclaringClass()).isEqualTo(Factory.class);
        assertThat(constructor.getParameterTypes()).containsExactly(String.class, Class.class);
        assertThat(constructor.getExceptionTypes()).isEmpty();
    }
}
