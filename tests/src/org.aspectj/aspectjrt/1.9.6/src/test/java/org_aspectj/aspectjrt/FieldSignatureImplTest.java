/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjrt;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;

import org.aspectj.lang.reflect.FieldSignature;
import org.aspectj.runtime.reflect.Factory;
import org.aspectj.runtime.reflect.FieldSignatureImpl;
import org.junit.jupiter.api.Test;

public class FieldSignatureImplTest {
    @Test
    void resolvesDeclaredFieldFromSignature() {
        Factory factory = new Factory("FieldSignatureImplTest.java", FieldSignatureImplTest.class);
        FieldSignature signature = factory.makeFieldSig(
                0,
                "fieldType",
                FieldSignatureImpl.class,
                Class.class);

        Field field = signature.getField();

        assertThat(field).isNotNull();
        assertThat(field.getDeclaringClass()).isEqualTo(FieldSignatureImpl.class);
        assertThat(field.getName()).isEqualTo("fieldType");
        assertThat(field.getType()).isEqualTo(Class.class);
    }
}
