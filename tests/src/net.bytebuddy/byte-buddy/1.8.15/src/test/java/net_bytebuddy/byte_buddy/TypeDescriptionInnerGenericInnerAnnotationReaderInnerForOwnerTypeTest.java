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

public class TypeDescriptionInnerGenericInnerAnnotationReaderInnerForOwnerTypeTest {
    @Test
    void resolvesOwnerTypeAnnotationsForLoadedNestedParameterizedField() {
        TypeDescription typeDescription = TypeDescription.ForLoadedType.of(OwnerTypeCarrier.class);

        FieldDescription fieldDescription = typeDescription.getDeclaredFields()
                .filter(named("nested"))
                .getOnly();
        TypeDescription.Generic nestedType = fieldDescription.getType();
        TypeDescription.Generic ownerType = nestedType.getOwnerType();

        assertThat(nestedType.asErasure().represents(Owner.Nested.class)).isTrue();
        assertThat(ownerType.asErasure().represents(Owner.class)).isTrue();
        assertThat(ownerType.getDeclaredAnnotations().isAnnotationPresent(OwnerTypeMarker.class)).isTrue();
    }

    @Target(ElementType.TYPE_USE)
    @Retention(RetentionPolicy.RUNTIME)
    private @interface OwnerTypeMarker {
    }

    private static class Owner<T> {
        private class Nested<S> {
        }
    }

    private static class OwnerTypeCarrier {
        private @OwnerTypeMarker Owner<String>.Nested<Integer> nested;
    }
}
