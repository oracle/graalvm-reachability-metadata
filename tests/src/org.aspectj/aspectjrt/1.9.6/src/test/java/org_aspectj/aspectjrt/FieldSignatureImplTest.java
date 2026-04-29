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
    void resolvesDeclaredFieldFromSignature() {
        FieldSignature signature = createSignature(
                "sampleValue",
                FieldOwner.class,
                String.class
        );

        Field field = signature.getField();

        assertThat(field).isNotNull();
        assertThat(field.getDeclaringClass()).isEqualTo(FieldOwner.class);
        assertThat(field.getName()).isEqualTo("sampleValue");
        assertThat(field.getType()).isEqualTo(String.class);
        assertThat(field.getModifiers()).isEqualTo(Modifier.PRIVATE | Modifier.FINAL);
    }

    private static FieldSignature createSignature(String name, Class<?> declaringType, Class<?> fieldType) {
        Factory factory = new Factory("FieldSignatureImplTest.java", FieldSignatureImplTest.class);
        return factory.makeFieldSig(Modifier.PRIVATE | Modifier.FINAL, name, declaringType, fieldType);
    }

    public static class FieldOwner {
        private final String sampleValue = "aspectj";
    }
}
