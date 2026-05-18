/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_quarkus_gizmo.gizmo2;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkus.gizmo2.Gizmo;

public class AnnotationCreatorTest {
    private static final String GENERATED_CLASS_NAME = "io_quarkus_gizmo.gizmo2.generated.AnnotationCopyTarget";
    private static final String GENERATED_CLASS_PATH = "io_quarkus_gizmo/gizmo2/generated/AnnotationCopyTarget.class";

    @Test
    void copiesValuesFromAnnotationInstance() {
        Map<String, byte[]> generatedClasses = new HashMap<>();
        SampleAnnotation annotation = new SampleAnnotationLiteral();
        Gizmo gizmo = Gizmo.create(generatedClasses::put);

        gizmo.class_(GENERATED_CLASS_NAME, classCreator -> classCreator.addAnnotation(annotation));

        assertThat(generatedClasses).containsOnlyKeys(GENERATED_CLASS_PATH);
        byte[] classBytes = generatedClasses.get(GENERATED_CLASS_PATH);
        assertThat(classBytes).isNotEmpty();

        String classFileText = new String(classBytes, StandardCharsets.ISO_8859_1);
        assertThat(classFileText)
                .contains("SampleAnnotation")
                .contains("name")
                .contains("generated")
                .contains("number")
                .contains("type");
    }

    public @interface SampleAnnotation {
        String name();

        int number();

        Class<?> type();
    }

    private static final class SampleAnnotationLiteral implements SampleAnnotation {
        @Override
        public String name() {
            return "generated";
        }

        @Override
        public int number() {
            return 42;
        }

        @Override
        public Class<?> type() {
            return String.class;
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return SampleAnnotation.class;
        }
    }
}
