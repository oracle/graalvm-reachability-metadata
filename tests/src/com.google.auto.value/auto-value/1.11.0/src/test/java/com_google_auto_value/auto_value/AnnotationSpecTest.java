/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_auto_value.auto_value;

import static org.assertj.core.api.Assertions.assertThat;

import autovalue.shaded.com.squareup.javapoet.AnnotationSpec;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.junit.jupiter.api.Test;

@AnnotationSpecTest.Label(value = "auto", rank = 11)
public class AnnotationSpecTest {
    @Test
    void rendersRuntimeAnnotationMembers() {
        Class<AnnotationSpecTest> annotationSpecTestAnnotationAccess = AnnotationSpecTest.class;
        Label label = annotationSpecTestAnnotationAccess.getAnnotation(Label.class);

        AnnotationSpec spec = AnnotationSpec.get(label);

        assertThat(spec.toString()).contains("value = \"auto\"", "rank = 11");
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Label {
        String value();

        int rank();
    }
}
