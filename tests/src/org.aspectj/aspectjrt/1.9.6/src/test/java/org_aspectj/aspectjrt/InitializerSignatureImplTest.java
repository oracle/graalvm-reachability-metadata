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
    void resolvesDeclaredNoArgumentConstructorFromInitializerSignature() {
        InitializerSignature signature = createInitializerSignature(InitializedComponent.class);

        Constructor<?> initializer = signature.getInitializer();

        assertThat(initializer).isNotNull();
        assertThat(initializer.getDeclaringClass()).isEqualTo(InitializedComponent.class);
        assertThat(initializer.getModifiers()).isEqualTo(Modifier.PUBLIC);
        assertThat(initializer.getParameterTypes()).isEmpty();
        assertThat(signature.getName()).isEqualTo("<init>");
    }

    private static InitializerSignature createInitializerSignature(Class<?> declaringType) {
        Factory factory = new Factory("InitializerSignatureImplTest.java", InitializerSignatureImplTest.class);
        return factory.makeInitializerSig(Modifier.PUBLIC, declaringType);
    }

    public static class InitializedComponent {
        private String state;

        {
            state = "initialized";
        }

        public String getState() {
            return state;
        }
    }
}
