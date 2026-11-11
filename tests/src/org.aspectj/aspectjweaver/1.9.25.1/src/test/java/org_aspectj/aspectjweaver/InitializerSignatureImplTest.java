/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjweaver;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import org.aspectj.lang.reflect.InitializerSignature;
import org.aspectj.runtime.reflect.Factory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class InitializerSignatureImplTest {
    @Test
    void resolvesDeclaredNoArgConstructorForInitializerSignature() {
        Factory factory = new Factory("InitializerSignatureImplTest.java", InitializerSignatureImplTest.class);
        InitializerSignature signature = factory.makeInitializerSig(Modifier.PUBLIC, InitializerFixture.class);

        Constructor constructor = signature.getInitializer();

        assertThat(constructor).isNotNull();
        assertThat(constructor.getDeclaringClass()).isEqualTo(InitializerFixture.class);
        assertThat(constructor.getParameterTypes()).isEmpty();
        assertThat(signature.getName()).isEqualTo("<init>");
    }

    public static final class InitializerFixture {
        public InitializerFixture() {
        }
    }
}
