/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_adapter_reflect;

import static org.assertj.core.api.Assertions.assertThat;

import io.sundr.model.AnnotationRef;
import io.sundr.model.Method;
import io.sundr.model.Property;
import io.sundr.model.TypeDef;
import io.sundr.reflect.ClassTo;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Test;

public class ClassToTest {

    @Test
    void convertsDeclaredMembersAndRuntimeAnnotationValues() {
        TypeDef typeDef = ClassTo.TYPEDEF.apply(ClassToFixture.class);

        assertThat(typeDef.getName()).isEqualTo("ClassToFixture");
        Method constructor = typeDef.getConstructors().stream()
                .findFirst()
                .orElseThrow();
        assertThat(typeDef.getConstructors()).hasSize(1);
        assertThat(annotationNamed(constructor, ClassToMarker.class).getParameters())
                .containsEntry("name", "constructor")
                .containsEntry("priority", 2);
        assertThat(typeDef.getProperties())
                .extracting(Property::getName)
                .contains("counter");
        assertThat(typeDef.getMethods())
                .extracting(Method::getName)
                .contains("compute");

        Property counter = typeDef.getProperties().stream()
                .filter(property -> property.getName().equals("counter"))
                .findFirst()
                .orElseThrow();
        assertThat(annotationNamed(counter, ClassToMarker.class).getParameters())
                .containsEntry("name", "field")
                .containsEntry("priority", 1);

        Method compute = typeDef.getMethods().stream()
                .filter(method -> method.getName().equals("compute"))
                .findFirst()
                .orElseThrow();
        AnnotationRef computeMarker = annotationNamed(compute, ClassToMarker.class);
        assertThat(computeMarker.getParameters())
                .containsEntry("name", "method")
                .containsEntry("priority", 3);
        assertThat((String[]) computeMarker.getParameters().get("tags"))
                .containsExactly("runtime", "values");
    }

    private static AnnotationRef annotationNamed(Property property, Class<?> annotationType) {
        return property.getAnnotations().stream()
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
    public @interface ClassToMarker {
        String name();

        int priority();

        String[] tags() default {};
    }

    public static class ClassToFixture {
        @ClassToMarker(name = "field", priority = 1)
        private int counter;

        @ClassToMarker(name = "constructor", priority = 2)
        public ClassToFixture() {
        }

        @ClassToMarker(name = "method", priority = 3, tags = {"runtime", "values"})
        public int compute(int value) {
            return counter + value;
        }
    }
}
