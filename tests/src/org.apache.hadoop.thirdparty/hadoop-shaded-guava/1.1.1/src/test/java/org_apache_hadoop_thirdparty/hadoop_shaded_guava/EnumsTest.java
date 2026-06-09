/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop_thirdparty.hadoop_shaded_guava;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Field;

import org.apache.hadoop.thirdparty.com.google.common.base.Enums;
import org.junit.jupiter.api.Test;

public class EnumsTest {
    @Test
    void getFieldReturnsEnumConstantFieldWithAnnotations() {
        Field field = Enums.getField(LifecycleState.RUNNING);

        assertThat(field.getName()).isEqualTo("RUNNING");
        assertThat(field.getDeclaringClass()).isSameAs(LifecycleState.class);
        assertThat(field.getType()).isSameAs(LifecycleState.class);
        assertThat(field.getAnnotation(DisplayName.class).value()).isEqualTo("Running");
    }

    private enum LifecycleState {
        QUEUED,

        @DisplayName("Running")
        RUNNING
    }

    @Retention(RetentionPolicy.RUNTIME)
    private @interface DisplayName {
        String value();
    }
}
