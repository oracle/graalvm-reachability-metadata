/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjrt;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import org.aspectj.lang.reflect.FieldSignature;
import org.aspectj.runtime.reflect.Factory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FieldSignatureImplTest {
    @Test
    void resolvesFieldDeclaredOnSignatureDeclaringType() {
        Factory factory = new Factory("FieldSignatureImplTest.java", FieldSignatureImplTest.class);
        FieldSignature signature = factory.makeFieldSig(
                Modifier.PRIVATE,
                "message",
                DeclaredFieldSubject.class,
                String.class);

        Field field = signature.getField();

        assertThat(field).isNotNull();
        assertThat(field.getDeclaringClass()).isEqualTo(DeclaredFieldSubject.class);
        assertThat(field.getName()).isEqualTo("message");
        assertThat(field.getType()).isEqualTo(String.class);
        assertThat(Modifier.isPrivate(field.getModifiers())).isTrue();
    }

    public static class DeclaredFieldSubject {
        private String message = "declared";
    }
}
