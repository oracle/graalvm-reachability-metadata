/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jdt_core_compiler.ecj;

import static org.assertj.core.api.Assertions.assertThat;

import javax.lang.model.type.TypeMirror;

import org.eclipse.jdt.internal.compiler.apt.model.PrimitiveTypeImpl;
import org.junit.jupiter.api.Test;

public class TypeMirrorImplTest {
    @Test
    void getAnnotationsByTypeReturnsTypedEmptyArrayForUnannotatedPrimitiveType() {
        final TypeMirror primitiveType = PrimitiveTypeImpl.INT;

        final TypeMirrorMarker[] annotations = primitiveType.getAnnotationsByType(TypeMirrorMarker.class);

        assertThat(annotations).isEmpty();
        assertThat(annotations.getClass().getComponentType()).isEqualTo(TypeMirrorMarker.class);
    }

    public @interface TypeMirrorMarker {
    }
}
