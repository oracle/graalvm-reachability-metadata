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

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.InitializerSignature;
import org.aspectj.runtime.reflect.Factory;
import org.junit.jupiter.api.Test;

public class InitializerSignatureImplTest {
    @Test
    void resolvesDeclaredConstructorForInstanceInitializerSignature() {
        Factory factory = new Factory("InitializerSignatureImplTest.java", InitializerSignatureImplTest.class);
        JoinPoint.StaticPart staticPart = factory.makeInitializerSJP(
                JoinPoint.INITIALIZATION,
                Modifier.PUBLIC,
                InitializerFixture.class,
                1);

        InitializerSignature signature = (InitializerSignature) staticPart.getSignature();
        Constructor<?> constructor = signature.getInitializer();

        assertThat(constructor).isNotNull();
        assertThat(constructor.getDeclaringClass()).isEqualTo(InitializerFixture.class);
        assertThat(constructor.getParameterTypes()).isEmpty();
        assertThat(signature.getDeclaringType()).isEqualTo(InitializerFixture.class);
        assertThat(signature.getName()).isEqualTo("<init>");
    }

    public static class InitializerFixture {
    }
}
