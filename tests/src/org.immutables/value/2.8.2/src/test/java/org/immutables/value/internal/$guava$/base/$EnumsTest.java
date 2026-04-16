/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.immutables.value.internal.$guava$.base;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

class EnumsTest {
    @Test
    void getsDeclaredFieldForEnumConstantWithSpecificClassBody() {
        final Field field = $Enums.getField(SampleEnum.SPECIAL);

        assertThat(field.getName()).isEqualTo("SPECIAL");
        assertThat(field.getDeclaringClass()).isEqualTo(SampleEnum.class);
        assertThat(field.isEnumConstant()).isTrue();
    }

    private enum SampleEnum {
        PLAIN,
        SPECIAL {
        }
    }
}
