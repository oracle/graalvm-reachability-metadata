/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_leangen_geantyref.geantyref;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Map;

import io.leangen.geantyref.TypeFactory;
import org.junit.jupiter.api.Test;

public class AnnotationInvocationHandlerTest {
    @Test
    void createsAnnotationWithPlatformAnnotationSemantics() throws Exception {
        SampleAnnotation generated = TypeFactory.annotation(
                SampleAnnotation.class,
                Map.of("value", "configured", "priority", 7));
        SampleAnnotation declared = AnnotatedType.class.getAnnotation(SampleAnnotation.class);

        assertThat(generated.value()).isEqualTo("configured");
        assertThat(generated.priority()).isEqualTo(7);
        assertThat(generated.annotationType()).isEqualTo(SampleAnnotation.class);
        assertThat(generated.equals(declared)).isTrue();
        assertThat(generated.hashCode()).isEqualTo(declared.hashCode());
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface SampleAnnotation {
        String value();

        int priority() default 0;
    }

    @SampleAnnotation(value = "configured", priority = 7)
    private static class AnnotatedType {
    }
}
