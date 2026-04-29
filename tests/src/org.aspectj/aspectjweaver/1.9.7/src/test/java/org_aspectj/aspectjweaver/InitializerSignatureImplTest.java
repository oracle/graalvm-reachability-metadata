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

import org.aspectj.lang.reflect.InitializerSignature;
import org.aspectj.runtime.reflect.Factory;
import org.junit.jupiter.api.Test;

public class InitializerSignatureImplTest {
    @Test
    void resolvesDeclaredInitializerConstructorFromSignature() {
        Factory factory = new Factory("InitializerSignatureImplTest.java", InitializerSignatureImplTest.class);
        InitializerSignature signature = factory.makeInitializerSig(Modifier.PUBLIC, InitializerFixture.class);

        Constructor<?> initializer = signature.getInitializer();

        assertThat(initializer).isNotNull();
        assertThat(initializer.getDeclaringClass()).isEqualTo(InitializerFixture.class);
        assertThat(initializer.getParameterTypes()).isEmpty();
    }

    public static class InitializerFixture {
        public InitializerFixture() {
        }
    }
}
