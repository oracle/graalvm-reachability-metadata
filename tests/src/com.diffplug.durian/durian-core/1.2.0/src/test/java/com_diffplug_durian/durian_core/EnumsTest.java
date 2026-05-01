/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_diffplug_durian.durian_core;

import java.lang.reflect.Field;

import com.diffplug.common.base.Enums;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class EnumsTest {
    @Test
    public void getFieldReturnsDeclaredEnumConstantField() {
        Field waitingField = Enums.getField(JobState.WAITING);
        Field runningField = Enums.getField(JobState.RUNNING);

        assertThat(waitingField.getName()).isEqualTo("WAITING");
        assertThat(waitingField.getDeclaringClass()).isEqualTo(JobState.class);
        assertThat(waitingField.getType()).isEqualTo(JobState.class);
        assertThat(waitingField.isEnumConstant()).isTrue();

        assertThat(runningField.getName()).isEqualTo("RUNNING");
        assertThat(runningField.getDeclaringClass()).isEqualTo(JobState.class);
        assertThat(runningField.getType()).isEqualTo(JobState.class);
        assertThat(runningField.isEnumConstant()).isTrue();
        assertThat(JobState.RUNNING.isActive()).isTrue();
    }

    private enum JobState {
        WAITING,
        RUNNING {
            @Override
            boolean isActive() {
                return true;
            }
        };

        boolean isActive() {
            return false;
        }
    }
}
