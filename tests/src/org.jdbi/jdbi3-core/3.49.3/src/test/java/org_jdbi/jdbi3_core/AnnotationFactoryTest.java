/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jdbi.jdbi3_core;

import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import org.jdbi.v3.core.qualifier.QualifiedType;
import org.jdbi.v3.core.qualifier.Qualifier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AnnotationFactoryTest {
    @Test
    void synthesizesValueAndArrayQualifiers() {
        Annotation first = qualifier(ScalarQualifier.class);
        Annotation second = qualifier(ScalarQualifier.class);
        Annotation array = qualifier(ArrayQualifier.class);

        assertThat(first).isEqualTo(second);
        assertThat(first.annotationType()).isEqualTo(ScalarQualifier.class);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
        assertThat(array.hashCode()).isNotZero();
    }

    private static Annotation qualifier(Class<? extends Annotation> type) {
        return QualifiedType.of(String.class).with(type).getQualifiers().iterator().next();
    }

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    public @interface ScalarQualifier {
        String value() default "primary";
    }

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    public @interface ArrayQualifier {
        int[] value() default {2, 3, 5};
    }
}
