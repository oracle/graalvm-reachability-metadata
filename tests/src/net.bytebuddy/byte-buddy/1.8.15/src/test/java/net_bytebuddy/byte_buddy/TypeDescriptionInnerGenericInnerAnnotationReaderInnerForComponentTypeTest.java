/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.type.TypeDescription;
import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.assertj.core.api.Assertions.assertThat;

public class TypeDescriptionInnerGenericInnerAnnotationReaderInnerForComponentTypeTest {
    @Test
    void resolvesComponentTypeAnnotationsForLoadedArrayField() {
        TypeDescription typeDescription = TypeDescription.ForLoadedType.of(ArrayFieldCarrier.class);

        FieldDescription fieldDescription = typeDescription.getDeclaredFields()
                .filter(named("componentMarked"))
                .getOnly();
        TypeDescription.Generic arrayType = fieldDescription.getType();
        TypeDescription.Generic componentType = arrayType.getComponentType();

        assertThat(arrayType.asErasure().represents(String[].class)).isTrue();
        assertThat(componentType.asErasure().represents(String.class)).isTrue();
        assertThat(componentType.getDeclaredAnnotations().isAnnotationPresent(ComponentMarker.class)).isTrue();
    }

    @Target(ElementType.TYPE_USE)
    @Retention(RetentionPolicy.RUNTIME)
    private @interface ComponentMarker {
    }

    private static class ArrayFieldCarrier {
        private @ComponentMarker String[] componentMarked;
    }
}
