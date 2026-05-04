/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjrt;

import org.aspectj.lang.reflect.FieldSignature;
import org.aspectj.lang.reflect.MethodSignature;
import org.aspectj.runtime.reflect.Factory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FactoryTest {
    @Test
    void resolvesStringBasedTypesWithBootstrapClassLoader() {
        Factory factory = new Factory("FactoryTest.java", String.class);

        MethodSignature signature = factory.makeMethodSig(
                "1",
                "substring",
                String.class.getName(),
                "int",
                "beginIndex",
                "",
                String.class.getName());

        assertThat(signature.getDeclaringType()).isEqualTo(String.class);
        assertThat(signature.getParameterTypes()).containsExactly(int.class);
        assertThat(signature.getParameterNames()).containsExactly("beginIndex");
        assertThat(signature.getExceptionTypes()).isEmpty();
        assertThat(signature.getReturnType()).isEqualTo(String.class);
    }

    @Test
    void resolvesStringBasedTypesWithApplicationClassLoader() {
        Factory factory = new Factory("FactoryTest.java", FactoryTest.class);

        FieldSignature signature = factory.makeFieldSig(
                "1",
                "value",
                ApplicationLoadedSubject.class.getName(),
                String.class.getName());

        assertThat(signature.getDeclaringType()).isEqualTo(ApplicationLoadedSubject.class);
        assertThat(signature.getFieldType()).isEqualTo(String.class);
        assertThat(signature.getName()).isEqualTo("value");
    }

    public static class ApplicationLoadedSubject {
        public String value;
    }
}
