/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import net.bytebuddy.description.type.TypeDescription;
import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static org.assertj.core.api.Assertions.assertThat;

public class TypeDescriptionInnerGenericInnerAnnotationReaderInnerForTypeVariableBoundTypeInnerOfFormalTypeVariableTest {
    @Test
    void resolvesAnnotatedBoundsForFormalTypeVariable() {
        TypeDescription typeDescription = TypeDescription.ForLoadedType.of(FormalTypeVariableBoundCarrier.class);
        TypeDescription.Generic typeVariable = typeDescription.getTypeVariables().get(0);
        TypeDescription.Generic bound = typeVariable.getUpperBounds().get(0);

        assertThat(typeVariable.getSymbol()).isEqualTo("T");
        assertThat(bound.asErasure().represents(Number.class)).isTrue();
        assertThat(bound.getDeclaredAnnotations().isAnnotationPresent(FormalTypeVariableBoundMarker.class)).isTrue();
    }

    @Target(ElementType.TYPE_USE)
    @Retention(RetentionPolicy.RUNTIME)
    private @interface FormalTypeVariableBoundMarker {
    }

    private static class FormalTypeVariableBoundCarrier<T extends @FormalTypeVariableBoundMarker Number> {
    }
}
