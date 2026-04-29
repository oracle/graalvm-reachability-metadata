/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjweaver;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import org.aspectj.lang.reflect.FieldSignature;
import org.aspectj.runtime.reflect.Factory;
import org.junit.jupiter.api.Test;

public class FieldSignatureImplTest {
    @Test
    void resolvesDeclaredFieldFromSignature() {
        Factory factory = new Factory("FieldSignatureImplTest.java", FieldSignatureImplTest.class);
        FieldSignature signature = factory.makeFieldSig(Modifier.PRIVATE, "trackedValue", FieldSignatureFixture.class,
                String.class);

        Field field = signature.getField();

        assertThat(field).isNotNull();
        assertThat(field.getDeclaringClass()).isEqualTo(FieldSignatureFixture.class);
        assertThat(field.getName()).isEqualTo("trackedValue");
        assertThat(field.getType()).isEqualTo(String.class);
        assertThat(signature.getFieldType()).isEqualTo(String.class);
    }
}

class FieldSignatureFixture {
    private String trackedValue = "aspectj-field-signature";
}
