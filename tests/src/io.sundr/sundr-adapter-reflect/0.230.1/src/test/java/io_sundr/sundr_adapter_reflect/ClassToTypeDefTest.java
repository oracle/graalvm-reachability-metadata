/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_adapter_reflect;

import static org.assertj.core.api.Assertions.assertThat;

import io.sundr.adapter.api.AdapterContext;
import io.sundr.adapter.reflect.ReflectionAdapter;
import io.sundr.model.AnnotationRef;
import io.sundr.model.Method;
import io.sundr.model.Property;
import io.sundr.model.TypeDef;
import io.sundr.model.repo.DefinitionRepository;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Test;

public class ClassToTypeDefTest {

    @Test
    void adaptsDeclaredMembersInnerTypesAndRuntimeAnnotationValues() {
        AdapterContext context = AdapterContext.create(DefinitionRepository.createRepository());
        ReflectionAdapter adapter = new ReflectionAdapter(context);

        TypeDef typeDef = adapter.adaptType(ReflectiveFixture.class);

        assertThat(typeDef.getName()).isEqualTo("ReflectiveFixture");
        assertThat(typeDef.getInnerTypes())
                .extracting(TypeDef::getName)
                .contains("NestedModel");
        assertThat(typeDef.getConstructors())
                .hasSize(1);
        assertThat(typeDef.getProperties())
                .extracting(Property::getName)
                .contains("counter");
        assertThat(typeDef.getMethods())
                .extracting(Method::getName)
                .contains("compute");

        AnnotationRef marker = annotationNamed(typeDef, ModelMarker.class);
        assertThat(marker.getParameters())
                .containsEntry("name", "fixture")
                .containsEntry("priority", 7);
        assertThat((String[]) marker.getParameters().get("tags"))
                .containsExactly("alpha", "beta");

        Method compute = typeDef.getMethods().stream()
                .filter(method -> method.getName().equals("compute"))
                .findFirst()
                .orElseThrow();
        assertThat(annotationNamed(compute, ModelMarker.class).getParameters())
                .containsEntry("name", "method")
                .containsEntry("priority", 3);
    }

    private static AnnotationRef annotationNamed(TypeDef typeDef, Class<?> annotationType) {
        return typeDef.getAnnotations().stream()
                .filter(annotation -> isAnnotationType(annotation, annotationType))
                .findFirst()
                .orElseThrow();
    }

    private static AnnotationRef annotationNamed(Method method, Class<?> annotationType) {
        return method.getAnnotations().stream()
                .filter(annotation -> isAnnotationType(annotation, annotationType))
                .findFirst()
                .orElseThrow();
    }

    private static boolean isAnnotationType(AnnotationRef annotation, Class<?> annotationType) {
        return annotation.getClassRef().getFullyQualifiedName().equals(modelName(annotationType));
    }

    private static String modelName(Class<?> type) {
        return type.getName().replace('$', '.');
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.FIELD, ElementType.CONSTRUCTOR, ElementType.METHOD})
    public @interface ModelMarker {
        String name();

        int priority();

        String[] tags() default {};
    }

    @ModelMarker(name = "fixture", priority = 7, tags = {"alpha", "beta"})
    public static class ReflectiveFixture {
        @ModelMarker(name = "field", priority = 1)
        private int counter;

        @ModelMarker(name = "constructor", priority = 2)
        public ReflectiveFixture() {
        }

        @ModelMarker(name = "method", priority = 3)
        public int compute(int value) {
            return counter + value;
        }

        @ModelMarker(name = "nested", priority = 4)
        public static class NestedModel {
        }
    }
}
