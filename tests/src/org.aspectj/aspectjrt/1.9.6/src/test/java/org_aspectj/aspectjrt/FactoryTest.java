/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjrt;

import static org.assertj.core.api.Assertions.assertThat;

import org.aspectj.lang.reflect.MethodSignature;
import org.aspectj.runtime.reflect.Factory;
import org.junit.jupiter.api.Test;

public class FactoryTest {
    @Test
    void resolvesSignatureTypesWithBootstrapClassLoader() {
        Factory factory = new Factory("String.java", String.class);

        MethodSignature signature = factory.makeMethodSig(
                "1",
                "substring",
                "java.lang.String",
                "int:int",
                "beginIndex:endIndex",
                "java.lang.IndexOutOfBoundsException",
                "java.lang.String");

        assertThat(signature.getDeclaringType()).isEqualTo(String.class);
        assertThat(signature.getParameterTypes()).containsExactly(int.class, int.class);
        assertThat(signature.getExceptionTypes()).containsExactly(IndexOutOfBoundsException.class);
        assertThat(signature.getReturnType()).isEqualTo(String.class);
    }

    @Test
    void resolvesSignatureTypesWithApplicationClassLoader() {
        Factory factory = new Factory("FactoryTest.java", FactoryTest.class);

        MethodSignature signature = factory.makeMethodSig(
                "1",
                "accept",
                "org_aspectj.aspectjrt.FactoryTest$SampleService",
                "org_aspectj.aspectjrt.FactoryTest$SamplePayload",
                "payload",
                "java.lang.IllegalArgumentException",
                "org_aspectj.aspectjrt.FactoryTest$SampleResult");

        assertThat(signature.getDeclaringType()).isEqualTo(SampleService.class);
        assertThat(signature.getParameterTypes()).containsExactly(SamplePayload.class);
        assertThat(signature.getExceptionTypes()).containsExactly(IllegalArgumentException.class);
        assertThat(signature.getReturnType()).isEqualTo(SampleResult.class);
    }

    public interface SampleService {
        SampleResult accept(SamplePayload payload);
    }

    public static class SamplePayload {
    }

    public static class SampleResult {
    }
}
