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
    @SuppressWarnings("checkstyle:annotationAccess")
    void adaptsDeclaredMembersNestedTypesAndAnnotationValues() {
        AdapterContext context = AdapterContext.create(DefinitionRepository.createRepository());
        ReflectionAdapter adapter = new ReflectionAdapter(context);

        TypeDef type = adapter.getTypeAdapterFunction().apply(AdaptedType.class);

        assertThat(type.getName()).isEqualTo("AdaptedType");
        assertThat(type.getProperties()).extracting(Property::getName).contains("count");
        assertThat(type.getConstructors()).extracting(Method::getArguments)
                .anySatisfy(arguments -> assertThat(arguments).hasSize(1));
        assertThat(type.getMethods()).extracting(Method::getName).contains("increment");
        assertThat(type.getInnerTypes()).extracting(TypeDef::getName).contains("NestedType");
        // Checkstyle: allow direct annotation access
        assertThat(type.getAnnotations()).extracting(AnnotationRef::getParameters)
                .anySatisfy(parameters -> assertThat(parameters).containsEntry("value", 7));
        // Checkstyle: disallow direct annotation access
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.FIELD, ElementType.CONSTRUCTOR, ElementType.METHOD})
    public @interface FixtureAnnotation {
        int value();
    }

    @FixtureAnnotation(7)
    public static class AdaptedType {
        @FixtureAnnotation(8)
        private int count;

        @FixtureAnnotation(9)
        public AdaptedType(int count) {
            this.count = count;
        }

        @FixtureAnnotation(10)
        public int increment(int amount) {
            return count + amount;
        }

        public static class NestedType {
        }
    }
}
