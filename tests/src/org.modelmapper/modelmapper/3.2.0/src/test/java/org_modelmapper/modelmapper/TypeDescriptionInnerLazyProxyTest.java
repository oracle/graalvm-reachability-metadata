/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_modelmapper.modelmapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.modelmapper.internal.bytebuddy.description.type.TypeDescription;

public class TypeDescriptionInnerLazyProxyTest {
    @Test
    void delegatesPredefinedProxyCallsToLoadedTypeDescriptions() {
        assertThat(TypeDescription.OBJECT.represents(Object.class)).isTrue();
        assertThat(TypeDescription.STRING.represents(String.class)).isTrue();
        assertThat(TypeDescription.CLASS.asErasure())
            .isEqualTo(TypeDescription.ForLoadedType.of(Class.class));
        assertThat(TypeDescription.VOID.isPrimitive()).isTrue();
    }
}
