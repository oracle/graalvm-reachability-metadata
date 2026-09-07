/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.apache.commons.lang3.AnnotationUtils;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import static org.assertj.core.api.Assertions.assertThat;

public class AnnotationUtilsTest {
    @Test
    @SuppressWarnings("checkstyle:annotationAccess")
    void comparesAndRendersAnnotationMembers() {
        Label first = First.class.getAnnotation(Label.class);
        Label second = Second.class.getAnnotation(Label.class);

        assertThat(AnnotationUtils.equals(first, second)).isTrue();
        assertThat(AnnotationUtils.hashCode(first)).isEqualTo(AnnotationUtils.hashCode(second));
        assertThat(AnnotationUtils.toString(first)).contains("value=metadata");
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Label {
        String value();
    }

    @Label("metadata")
    public static class First {}

    @Label("metadata")
    public static class Second {}
}
