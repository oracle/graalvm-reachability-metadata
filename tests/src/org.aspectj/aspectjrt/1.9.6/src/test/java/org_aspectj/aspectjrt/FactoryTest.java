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
    void createsMethodSignatureUsingBootstrapClassLookup() {
        Factory factory = new Factory("String.java", String.class);

        MethodSignature signature = factory.makeMethodSig(
                "1",
                "substring",
                String.class.getName(),
                "int:int",
                "beginIndex:endIndex",
                RuntimeException.class.getName(),
                String.class.getName());

        assertThat(signature.getDeclaringType()).isEqualTo(String.class);
        assertThat(signature.getName()).isEqualTo("substring");
        assertThat(signature.getParameterTypes()).containsExactly(int.class, int.class);
        assertThat(signature.getParameterNames()).containsExactly("beginIndex", "endIndex");
        assertThat(signature.getExceptionTypes()).containsExactly(RuntimeException.class);
        assertThat(signature.getReturnType()).isEqualTo(String.class);
    }

    @Test
    void createsMethodSignatureUsingLexicalClassLoaderLookup() {
        Factory factory = new Factory("FactoryTest.java", FactoryTest.class);

        MethodSignature signature = factory.makeMethodSig(
                "1",
                "formatValue",
                FactoryTest.class.getName(),
                StringBuilder.class.getName() + ":int",
                "builder:count",
                IllegalArgumentException.class.getName(),
                String.class.getName());

        assertThat(signature.getDeclaringType()).isEqualTo(FactoryTest.class);
        assertThat(signature.getName()).isEqualTo("formatValue");
        assertThat(signature.getParameterTypes()).containsExactly(StringBuilder.class, int.class);
        assertThat(signature.getParameterNames()).containsExactly("builder", "count");
        assertThat(signature.getExceptionTypes()).containsExactly(IllegalArgumentException.class);
        assertThat(signature.getReturnType()).isEqualTo(String.class);
    }

    public String formatValue(StringBuilder builder, int count) {
        if (count < 0) {
            throw new IllegalArgumentException("count must be non-negative");
        }
        return builder.toString().repeat(count);
    }
}
