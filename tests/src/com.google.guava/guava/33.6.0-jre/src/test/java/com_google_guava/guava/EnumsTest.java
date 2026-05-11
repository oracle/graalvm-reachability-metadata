/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_guava.guava;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.base.Enums;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

public class EnumsTest {
    @Test
    void getFieldReturnsEnumConstantField() {
        Field field = Enums.getField(TaskState.RUNNING);

        assertThat(field.getName()).isEqualTo("RUNNING");
        assertThat(field.getDeclaringClass()).isSameAs(TaskState.class);
        assertThat(field.getAnnotation(DisplayName.class).value()).isEqualTo("running task");
    }

    private enum TaskState {
        QUEUED,

        @DisplayName("running task")
        RUNNING
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    private @interface DisplayName {
        String value();
    }
}
