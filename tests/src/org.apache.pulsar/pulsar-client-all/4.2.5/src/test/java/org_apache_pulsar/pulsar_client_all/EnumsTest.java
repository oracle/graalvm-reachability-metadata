/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;

import org.apache.pulsar.shade.com.google.common.base.Enums;
import org.junit.jupiter.api.Test;

public class EnumsTest {
    @Test
    void returnsFieldDeclaringRequestedEnumConstant() {
        Field field = Enums.getField(State.READY);

        assertThat(field.getName()).isEqualTo("READY");
        assertThat(field.getDeclaringClass()).isEqualTo(State.class);
        assertThat(field.isEnumConstant()).isTrue();
    }

    private enum State {
        READY
    }
}
