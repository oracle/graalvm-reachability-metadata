/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjrt;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import org.aspectj.lang.reflect.InitializerSignature;
import org.aspectj.runtime.reflect.Factory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class InitializerSignatureImplTest {
    @Test
    void resolvesInitializerConstructorDeclaredOnSignatureDeclaringType() {
        Factory factory = new Factory("InitializerSignatureImplTest.java", InitializerSignatureImplTest.class);
        InitializerSignature signature = factory.makeInitializerSig(Modifier.PUBLIC, InitializerSubject.class);

        Constructor<?> constructor = signature.getInitializer();

        assertThat(constructor).isNotNull();
        assertThat(constructor.getDeclaringClass()).isEqualTo(InitializerSubject.class);
        assertThat(constructor.getParameterTypes()).isEmpty();
        assertThat(signature.getName()).isEqualTo("<init>");
    }

    public static class InitializerSubject {
        public String initializedValue;

        {
            initializedValue = "initialized";
        }
    }
}
