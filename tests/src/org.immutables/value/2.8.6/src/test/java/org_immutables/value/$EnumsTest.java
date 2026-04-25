/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_immutables.value;

import java.lang.reflect.Field;

import org.immutables.value.internal.$guava$.base.$Enums;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EnumsTest {

    @Test
    void getFieldReturnsDeclaredEnumFieldForRegularAndConstantSpecificEnumConstants() {
        Field readyField = $Enums.getField(SampleStatus.READY);
        Field customizedField = $Enums.getField(SampleStatus.CUSTOMIZED);

        assertThat(readyField.getName()).isEqualTo("READY");
        assertThat(readyField.getDeclaringClass()).isEqualTo(SampleStatus.class);
        assertThat(readyField.isEnumConstant()).isTrue();

        assertThat(customizedField.getName()).isEqualTo("CUSTOMIZED");
        assertThat(customizedField.getDeclaringClass()).isEqualTo(SampleStatus.class);
        assertThat(customizedField.isEnumConstant()).isTrue();
        assertThat(SampleStatus.CUSTOMIZED.customized()).isTrue();
    }

    private enum SampleStatus {
        READY,
        CUSTOMIZED {
            @Override
            boolean customized() {
                return true;
            }
        };

        boolean customized() {
            return false;
        }
    }
}
