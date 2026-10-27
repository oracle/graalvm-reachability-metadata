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
    void adaptsMembersAndAnnotationParameters() {
        TypeDef type = ClassTo.TYPEDEF.apply(AnnotatedType.class);

        assertThat(type.getName()).isEqualTo("AnnotatedType");
        assertThat(type.getProperties()).extracting(Property::getName).contains("count");
        assertThat(type.getProperties()).extracting(Property::getAnnotations)
                .anySatisfy(annotations -> assertThat(annotations).extracting(AnnotationRef::getParameters)
                        .anySatisfy(parameters -> assertThat(parameters).containsEntry("value", 8)));
        assertThat(type.getConstructors()).extracting(Method::getArguments)
                .anySatisfy(arguments -> assertThat(arguments).hasSize(1));
        assertThat(type.getMethods()).extracting(Method::getName).contains("increment");
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.CONSTRUCTOR, ElementType.METHOD})
    public @interface FixtureAnnotation {
        int value();
    }

    public static class AnnotatedType {
        @FixtureAnnotation(8)
        private int count;

        @FixtureAnnotation(9)
        public AnnotatedType(int count) {
            this.count = count;
        }

        @FixtureAnnotation(10)
        public int increment(int amount) {
            return count + amount;
        }
    }
}
