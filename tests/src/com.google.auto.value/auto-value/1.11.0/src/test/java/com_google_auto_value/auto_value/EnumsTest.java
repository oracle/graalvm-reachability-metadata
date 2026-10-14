/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_auto_value.auto_value;

import static org.assertj.core.api.Assertions.assertThat;

import autovalue.shaded.com.google.common.base.Enums;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

public class EnumsTest {
    @Test
    void returnsTheFieldDeclaringAnEnumConstant() {
        Field field = Enums.getField(State.READY);

        assertThat(field.getName()).isEqualTo("READY");
        assertThat(field.getType()).isEqualTo(State.class);
    }

    private enum State {
        READY
    }
}
