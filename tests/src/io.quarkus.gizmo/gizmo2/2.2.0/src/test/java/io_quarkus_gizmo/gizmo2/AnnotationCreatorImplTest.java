/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_quarkus_gizmo.gizmo2;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import io.quarkus.gizmo2.Gizmo;
import io.quarkus.gizmo2.creator.AnnotationCreator;

public class AnnotationCreatorImplTest {
    private static final String GENERATED_CLASS_NAME = "io_quarkus_gizmo.gizmo2.generated.AnnotationCreatorImplTarget";
    private static final String GENERATED_CLASS_PATH = "io_quarkus_gizmo/gizmo2/generated/AnnotationCreatorImplTarget.class";

    @Test
    void addsNestedAnnotationValuesUsingMethodReferences() {
        Map<String, byte[]> generatedClasses = new HashMap<>();
        Gizmo gizmo = Gizmo.create(generatedClasses::put);

        gizmo.class_(GENERATED_CLASS_NAME, classCreator -> classCreator.addAnnotation(ContainerAnnotation.class, annotation -> {
            annotation.add(ContainerAnnotation::nested, nested -> nested.add(NestedAnnotation::value, "single"));

            List<Consumer<AnnotationCreator<NestedAnnotation>>> nestedBuilders = List.of(
                    nested -> nested.add(NestedAnnotation::value, "first"),
                    nested -> nested.add(NestedAnnotation::value, "second"));
            annotation.addArray(ContainerAnnotation::nestedArray, nestedBuilders);
        }));

        assertThat(generatedClasses).containsOnlyKeys(GENERATED_CLASS_PATH);
        byte[] classBytes = generatedClasses.get(GENERATED_CLASS_PATH);
        assertThat(classBytes).isNotEmpty();

        String classFileText = new String(classBytes, StandardCharsets.ISO_8859_1);
        assertThat(classFileText)
                .contains("ContainerAnnotation")
                .contains("nested")
                .contains("NestedAnnotation")
                .contains("single")
                .contains("nestedArray")
                .contains("first")
                .contains("second");
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface ContainerAnnotation {
        NestedAnnotation nested();

        NestedAnnotation[] nestedArray();
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface NestedAnnotation {
        String value();
    }
}
