/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_quarkus_gizmo.gizmo2;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.constant.ClassDesc;

import org.junit.jupiter.api.Test;

import io.quarkus.gizmo2.desc.FieldDesc;

public class FieldDescTest {
    @Test
    void createsDescriptorFromDeclaredField() {
        FieldDesc fieldDesc = FieldDesc.of(String.class, "CASE_INSENSITIVE_ORDER");

        assertThat(fieldDesc.owner()).isEqualTo(ClassDesc.of("java.lang.String"));
        assertThat(fieldDesc.name()).isEqualTo("CASE_INSENSITIVE_ORDER");
        assertThat(fieldDesc.type()).isEqualTo(ClassDesc.of("java.util.Comparator"));
        assertThat(fieldDesc.toString()).contains("java.lang.String", "CASE_INSENSITIVE_ORDER", "java.util.Comparator");
    }
}
