/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jdt_core_compiler.ecj;

import static org.assertj.core.api.Assertions.assertThat;

import javax.lang.model.type.NoType;

import org.eclipse.jdt.internal.compiler.apt.model.NoTypeImpl;
import org.junit.jupiter.api.Test;

public class NoTypeImplTest {
    @Test
    void getAnnotationsByTypeReturnsTypedEmptyArray() {
        final NoType noType = NoTypeImpl.NO_TYPE_VOID;

        final NoTypeMarker[] annotations = noType.getAnnotationsByType(NoTypeMarker.class);

        assertThat(annotations).isEmpty();
    }

    public @interface NoTypeMarker {
    }
}
