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
import java.util.List;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.assertj.core.api.Assertions.assertThat;

public class TypeDescriptionInnerGenericInnerAnnotationReaderInnerForWildcardUpperBoundTypeTest {
    @Test
    void resolvesUpperBoundAnnotationsForLoadedWildcardField() {
        TypeDescription typeDescription = TypeDescription.ForLoadedType.of(WildcardUpperBoundCarrier.class);

        FieldDescription fieldDescription = typeDescription.getDeclaredFields()
                .filter(named("values"))
                .getOnly();
        TypeDescription.Generic parameterizedType = fieldDescription.getType();
        TypeDescription.Generic wildcardType = parameterizedType.getTypeArguments().get(0);
        TypeDescription.Generic upperBound = wildcardType.getUpperBounds().get(0);

        assertThat(parameterizedType.asErasure().represents(List.class)).isTrue();
        assertThat(wildcardType.getSort().isWildcard()).isTrue();
        assertThat(upperBound.asErasure().represents(Number.class)).isTrue();
        assertThat(((java.util.function.Supplier<net.bytebuddy.description.annotation.AnnotationList>) upperBound::getDeclaredAnnotations)
                .get()
                .ofType(WildcardUpperBoundMarker.class)
                .getAnnotationType()
                .represents(WildcardUpperBoundMarker.class)).isTrue();
    }

    @Target(ElementType.TYPE_USE)
    @Retention(RetentionPolicy.RUNTIME)
    private @interface WildcardUpperBoundMarker {
    }

    private static class WildcardUpperBoundCarrier {
        private List<? extends @WildcardUpperBoundMarker Number> values;
    }
}
