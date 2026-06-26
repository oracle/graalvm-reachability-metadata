/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_lang3;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import org.apache.commons.lang3.AnnotationUtils;
import org.junit.jupiter.api.Test;

public class AnnotationUtilsTest {

    @SuppressWarnings("checkstyle:annotationAccess")
    @Test
    public void equalsUsesAnnotationMembersFromMatchingRuntimeAnnotations() {
        Composite sameLeft = MatchingAnnotationLeft.class.getAnnotation(Composite.class);
        Composite sameRight = MatchingAnnotationRight.class.getAnnotation(Composite.class);
        Composite different = DifferentAnnotationValues.class.getAnnotation(Composite.class);

        assertThat(AnnotationUtils.equals(sameLeft, sameRight)).isTrue();
        assertThat(AnnotationUtils.equals(sameLeft, different)).isFalse();
    }

    @SuppressWarnings("checkstyle:annotationAccess")
    @Test
    public void hashCodeAndToStringUseAnnotationMembersFromRuntimeAnnotation() {
        Composite annotation = MatchingAnnotationLeft.class.getAnnotation(Composite.class);

        assertThat(AnnotationUtils.hashCode(annotation)).isEqualTo(annotation.hashCode());
        assertThat(AnnotationUtils.toString(annotation))
                .contains("@" + Composite.class.getName())
                .contains("value=alpha")
                .contains("numbers=[1,2,3]")
                .contains("nested=@" + Nested.class.getName())
                .contains("name=inner");
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Nested {
        String name();
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Composite {
        String value();

        int[] numbers();

        Nested nested();
    }

    @Composite(value = "alpha", numbers = {1, 2, 3}, nested = @Nested(name = "inner"))
    public static class MatchingAnnotationLeft {
    }

    @Composite(value = "alpha", numbers = {1, 2, 3}, nested = @Nested(name = "inner"))
    public static class MatchingAnnotationRight {
    }

    @Composite(value = "beta", numbers = {5, 8}, nested = @Nested(name = "other"))
    public static class DifferentAnnotationValues {
    }
}
