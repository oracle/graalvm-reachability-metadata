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

public class TypeDescriptionInnerGenericInnerAnnotationReaderInnerForTypeVariableBoundTypeTest {
    @Test
    void resolvesBoundAnnotationsForLoadedTypeVariableField() {
        TypeDescription typeDescription = TypeDescription.ForLoadedType.of(TypeVariableBoundCarrier.class);

        FieldDescription fieldDescription = typeDescription.getDeclaredFields()
                .filter(named("value"))
                .getOnly();
        TypeDescription.Generic typeVariable = fieldDescription.getType();
        TypeDescription.Generic bound = typeVariable.getUpperBounds().get(0);

        assertThat(typeVariable.getSymbol()).isEqualTo("T");
        assertThat(bound.asErasure().represents(Number.class)).isTrue();
        assertThat(bound.getDeclaredAnnotations().isAnnotationPresent(TypeVariableBoundMarker.class)).isTrue();
    }

    @Target(ElementType.TYPE_USE)
    @Retention(RetentionPolicy.RUNTIME)
    private @interface TypeVariableBoundMarker {
    }

    private static class TypeVariableBoundCarrier<T extends @TypeVariableBoundMarker Number> {
        private T value;
    }
}
