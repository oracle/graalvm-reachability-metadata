/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.javapoet.AnnotationSpec;

/** Verifies Java source annotation rendering from a runtime annotation. */
public class AnnotationSpecTest {
    @Test
    void rendersExplicitMembersAndOptionallyDefaultValues() {
        Route annotation = MergedAnnotations.from(Endpoint.class).get(Route.class).synthesize();

        AnnotationSpec explicit = AnnotationSpec.get(annotation);
        AnnotationSpec includingDefaults = AnnotationSpec.get(annotation, true);

        assertThat(explicit.toString()).contains("path = \"/orders\"", "methods = {\"GET\", \"POST\"}")
                .doesNotContain("secure");
        assertThat(includingDefaults.toString()).contains("secure = true");
        assertThat(explicit.members()).containsOnlyKeys("methods", "path");
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Route {
        String path();
        String[] methods();
        boolean secure() default true;
    }

    @Route(path = "/orders", methods = {"GET", "POST"})
    public static class Endpoint { }
}
