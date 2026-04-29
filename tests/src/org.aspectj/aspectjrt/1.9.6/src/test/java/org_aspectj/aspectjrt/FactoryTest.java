/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjrt;

import java.lang.reflect.Modifier;

import org.aspectj.lang.reflect.MethodSignature;
import org.aspectj.runtime.reflect.Factory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FactoryTest {

    @Test
    void createsStringBasedMethodSignatureUsingBootstrapLoadedLexicalClass() {
        Factory factory = new Factory("BootstrapLoaded.java", String.class);

        MethodSignature signature = factory.makeMethodSig(
                Integer.toHexString(Modifier.PUBLIC),
                "substring",
                String.class.getName(),
                Integer.class.getName(),
                "beginIndex",
                IllegalArgumentException.class.getName(),
                String.class.getName()
        );

        assertThat(signature.getDeclaringType()).isEqualTo(String.class);
        assertThat(signature.getParameterTypes()).containsExactly(Integer.class);
        assertThat(signature.getParameterNames()).containsExactly("beginIndex");
        assertThat(signature.getExceptionTypes()).containsExactly(IllegalArgumentException.class);
        assertThat(signature.getReturnType()).isEqualTo(String.class);
    }

    @Test
    void createsStringBasedMethodSignatureUsingApplicationLoadedLexicalClass() {
        Factory factory = new Factory("ApplicationLoaded.java", FactoryTest.class);

        MethodSignature signature = factory.makeMethodSig(
                Integer.toHexString(Modifier.PUBLIC),
                "sampleOperation",
                FactoryTest.class.getName(),
                String.class.getName(),
                "value",
                IllegalStateException.class.getName(),
                Boolean.class.getName()
        );

        assertThat(signature.getDeclaringType()).isEqualTo(FactoryTest.class);
        assertThat(signature.getParameterTypes()).containsExactly(String.class);
        assertThat(signature.getParameterNames()).containsExactly("value");
        assertThat(signature.getExceptionTypes()).containsExactly(IllegalStateException.class);
        assertThat(signature.getReturnType()).isEqualTo(Boolean.class);
    }

    public Boolean sampleOperation(String value) {
        return !value.isEmpty();
    }
}
