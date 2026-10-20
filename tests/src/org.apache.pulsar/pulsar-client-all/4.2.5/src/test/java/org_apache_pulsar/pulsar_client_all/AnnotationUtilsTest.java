/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import org.apache.pulsar.shade.org.apache.commons.lang3.AnnotationUtils;
import org.junit.jupiter.api.Test;

public class AnnotationUtilsTest {
    @SuppressWarnings("checkstyle:annotationAccess")
    @Test
    void comparesHashesAndFormatsRuntimeAnnotationMembers() {
        Composite first = FirstAnnotated.class.getAnnotation(Composite.class);
        Composite matching = MatchingAnnotated.class.getAnnotation(Composite.class);
        Composite different = DifferentAnnotated.class.getAnnotation(Composite.class);

        assertThat(AnnotationUtils.equals(first, matching)).isTrue();
        assertThat(AnnotationUtils.equals(first, different)).isFalse();
        assertThat(AnnotationUtils.hashCode(first)).isEqualTo(first.hashCode());
        assertThat(AnnotationUtils.toString(first))
                .contains("value=alpha")
                .contains("numbers=[1,2,3]")
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
    public static class FirstAnnotated {
    }

    @Composite(value = "alpha", numbers = {1, 2, 3}, nested = @Nested(name = "inner"))
    public static class MatchingAnnotated {
    }

    @Composite(value = "beta", numbers = {5, 8}, nested = @Nested(name = "other"))
    public static class DifferentAnnotated {
    }
}
