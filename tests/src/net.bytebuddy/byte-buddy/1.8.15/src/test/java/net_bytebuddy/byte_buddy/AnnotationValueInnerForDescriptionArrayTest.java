/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.annotation.AnnotationValue;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AnnotationValueInnerForDescriptionArrayTest {
    @Test
    void resolvesTypeArrayDescriptionFromAnnotationBuilder() throws Exception {
        AnnotationValue<?, ?> value = typeArrayAnnotationValue();

        TypeDescription[] resolved = (TypeDescription[]) value.resolve();

        assertThat(resolved)
                .hasSize(2);
        assertThat(resolved[0].represents(String.class)).isTrue();
        assertThat(resolved[1].represents(Integer.class)).isTrue();
    }

    @Test
    void loadsTypeArrayDescriptionWithSuppliedClassLoader() throws Exception {
        AnnotationValue<?, ?> value = typeArrayAnnotationValue();

        AnnotationValue.Loaded<?> loaded = value.load(getClass().getClassLoader());

        assertThat(loaded.getState().isResolved()).isTrue();
        assertThat((Class<?>[]) loaded.resolve()).containsExactly(String.class, Integer.class);
    }

    private static AnnotationValue<?, ?> typeArrayAnnotationValue() throws Exception {
        AnnotationDescription description = AnnotationDescription.Builder
                .ofType(TypeArrayAnnotation.class)
                .defineTypeArray("value", String.class, Integer.class)
                .build();

        return description.getValue(new MethodDescription.ForLoadedMethod(
                TypeArrayAnnotation.class.getMethod("value")));
    }

    public @interface TypeArrayAnnotation {
        Class<?>[] value();
    }
}
