/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_diffplug_durian.durian_core;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Field;

import com.diffplug.common.base.Enums;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class EnumsTest {
    @Test
    void getFieldReturnsDeclaringFieldForEnumConstant() {
        Field field = Enums.getField(FeatureFlag.ENABLED);

        assertThat(field.getDeclaringClass()).isEqualTo(FeatureFlag.class);
        assertThat(field.getName()).isEqualTo("ENABLED");
        Field fieldAnnotationAccess = field;
        assertThat(fieldAnnotationAccess.getAnnotation(Label.class).value()).isEqualTo("enabled flag");
    }

    private enum FeatureFlag {
        @Label("enabled flag")
        ENABLED,
        DISABLED
    }

    @Retention(RetentionPolicy.RUNTIME)
    private @interface Label {
        String value();
    }
}
