/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjweaver;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.aspectj.lang.reflect.MethodSignature;
import org.aspectj.runtime.reflect.Factory;
import org.junit.jupiter.api.Test;

public class FactoryTest {
    @Test
    void createsMethodSignatureUsingBootstrapClassLookup() {
        Factory factory = new Factory("String.java", String.class);

        MethodSignature signature = factory.makeMethodSig("1", "substring", String.class.getName(), "int:int",
                "beginIndex:endIndex", "java.lang.IndexOutOfBoundsException", String.class.getName());

        assertThat(signature.getDeclaringType()).isEqualTo(String.class);
        assertThat(signature.getName()).isEqualTo("substring");
        assertThat(signature.getParameterTypes()).containsExactly(int.class, int.class);
        assertThat(signature.getParameterNames()).containsExactly("beginIndex", "endIndex");
        assertThat(signature.getExceptionTypes()).containsExactly(IndexOutOfBoundsException.class);
        assertThat(signature.getReturnType()).isEqualTo(String.class);
    }

    @Test
    void createsMethodSignatureUsingLexicalClassLoaderLookup() {
        Factory factory = new Factory("FactoryTest.java", FactoryTest.class);

        MethodSignature signature = factory.makeMethodSig("9", "copyOf", Arrays.class.getName(),
                Object[].class.getName() + ":int", "original:newLength",
                NegativeArraySizeException.class.getName(), Object[].class.getName());

        assertThat(signature.getDeclaringType()).isEqualTo(Arrays.class);
        assertThat(signature.getName()).isEqualTo("copyOf");
        assertThat(signature.getParameterTypes()).containsExactly(Object[].class, int.class);
        assertThat(signature.getParameterNames()).containsExactly("original", "newLength");
        assertThat(signature.getExceptionTypes()).containsExactly(NegativeArraySizeException.class);
        assertThat(signature.getReturnType()).isEqualTo(Object[].class);
    }
}
