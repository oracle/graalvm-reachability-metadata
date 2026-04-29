/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_modelmapper.modelmapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;

import org.junit.jupiter.api.Test;
import org.modelmapper.internal.bytebuddy.description.type.TypeDescription;

public class TypeDescriptionInnerGenericInnerLazyProxyTest {
    @Test
    void delegatesGenericProxyCallsToLoadedTypeDescriptions() {
        assertThat(TypeDescription.Generic.OBJECT.represents(Object.class)).isTrue();
        assertThat(TypeDescription.Generic.CLASS.asErasure())
            .isEqualTo(TypeDescription.ForLoadedType.of(Class.class));
        assertThat(TypeDescription.Generic.VOID.isPrimitive()).isTrue();
        assertThat(TypeDescription.Generic.ANNOTATION.asErasure())
            .isEqualTo(TypeDescription.ForLoadedType.of(Annotation.class));
    }
}
