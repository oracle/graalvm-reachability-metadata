/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_swagger_core_v3.swagger_core;

import io.swagger.v3.core.util.ValidationAnnotationsUtils;
import org.junit.jupiter.api.Test;

import javax.validation.OverridesAttribute;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static org.assertj.core.api.Assertions.assertThat;

public class ValidationAnnotationsUtilsTest {
    @Test
    void expandsComposedConstraintWithoutOverriddenConstraint() {
        Annotation[] expanded = ValidationAnnotationsUtils.expandValidationMetaAnnotations(
                new Annotation[] {new BoundedValueAnnotation()});
        boolean containsBoundedValue = false;
        boolean containsMaximum = false;
        boolean containsMinimum = false;
        for (Annotation annotation : expanded) {
            containsBoundedValue |= annotation.annotationType().equals(BoundedValue.class);
            containsMaximum |= annotation.annotationType().equals(Max.class);
            containsMinimum |= annotation.annotationType().equals(Min.class);
        }

        assertThat(containsBoundedValue).isTrue();
        assertThat(containsMaximum).isTrue();
        assertThat(containsMinimum).isFalse();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({FIELD, ANNOTATION_TYPE})
    @Min(1)
    @Max(100)
    public @interface BoundedValue {
        @OverridesAttribute(constraint = Min.class, name = "value")
        long minimum() default 10;
    }

    public static final class BoundedValueAnnotation implements BoundedValue {
        @Override
        public long minimum() {
            return 10;
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return BoundedValue.class;
        }
    }
}
