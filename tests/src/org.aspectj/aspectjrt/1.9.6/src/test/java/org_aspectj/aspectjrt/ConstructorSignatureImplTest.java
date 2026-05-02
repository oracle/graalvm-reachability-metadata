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

import org.aspectj.lang.NoAspectBoundException;
import org.aspectj.lang.reflect.ConstructorSignature;
import org.aspectj.runtime.reflect.Factory;
import org.junit.jupiter.api.Test;

public class ConstructorSignatureImplTest {
    private static final Class<?>[] NO_PARAMETER_TYPES = new Class<?>[0];
    private static final String[] NO_PARAMETER_NAMES = new String[0];
    private static final Class<?>[] NO_EXCEPTION_TYPES = new Class<?>[0];

    @Test
    void resolvesDeclaredNoArgumentConstructorFromSignatureType() {
        Factory factory = new Factory("NoAspectBoundException.java", NoAspectBoundException.class);
        ConstructorSignature signature = factory.makeConstructorSig(
                Modifier.PUBLIC,
                NoAspectBoundException.class,
                NO_PARAMETER_TYPES,
                NO_PARAMETER_NAMES,
                NO_EXCEPTION_TYPES);

        Constructor<?> constructor = signature.getConstructor();

        assertThat(constructor).isNotNull();
        assertThat(constructor.getDeclaringClass()).isEqualTo(NoAspectBoundException.class);
        assertThat(constructor.getParameterTypes()).isEmpty();
        assertThat(constructor.getExceptionTypes()).isEmpty();
        assertThat(signature.getName()).isEqualTo("<init>");
    }
}
