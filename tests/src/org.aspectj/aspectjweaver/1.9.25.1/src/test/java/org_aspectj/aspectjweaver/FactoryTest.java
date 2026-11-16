/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjweaver;

import org.aspectj.lang.reflect.FieldSignature;
import org.aspectj.runtime.reflect.Factory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FactoryTest {
    @Test
    void createsSignatureTypesWithBootstrapClassLoader() {
        Factory factory = new Factory("Object.java", Object.class);

        FieldSignature signature = factory.makeFieldSig("1", "value", "java.lang.Object", "java.lang.String");

        assertThat(signature.getName()).isEqualTo("value");
        assertThat(signature.getDeclaringType()).isEqualTo(Object.class);
        assertThat(signature.getFieldType()).isEqualTo(String.class);
    }

    @Test
    void createsSignatureTypesWithApplicationClassLoader() {
        Factory factory = new Factory("FactoryTest.java", FactoryTest.class);

        FieldSignature signature = factory.makeFieldSig(
                "1",
                "fixture",
                FactoryTest.class.getName(),
                SignatureFixture.class.getName()
        );

        assertThat(signature.getName()).isEqualTo("fixture");
        assertThat(signature.getDeclaringType()).isEqualTo(FactoryTest.class);
        assertThat(signature.getFieldType()).isEqualTo(SignatureFixture.class);
    }

    public static final class SignatureFixture {
        private SignatureFixture() {
        }
    }
}
