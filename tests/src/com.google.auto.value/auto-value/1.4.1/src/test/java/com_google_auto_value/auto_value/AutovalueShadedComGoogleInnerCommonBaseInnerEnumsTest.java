/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_auto_value.auto_value;

import autovalue.shaded.com.google$.auto.common.$Visibility;
import autovalue.shaded.com.google$.common.base.$Enums;

import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AutovalueShadedComGoogleInnerCommonBaseInnerEnumsTest {
    @Test
    void getFieldReturnsEnumConstantFieldForShadedEnum() {
        Field field = $Enums.getField($Visibility.PUBLIC);

        assertThat(field.getName()).isEqualTo("PUBLIC");
        assertThat(field.getDeclaringClass()).isEqualTo($Visibility.class);
        assertThat(field.getType()).isEqualTo($Visibility.class);
        assertThat(field.isEnumConstant()).isTrue();
    }
}
