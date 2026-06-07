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

public class TypeDescriptionInnerGenericInnerAnnotationReaderInnerDispatcherInnerForJava8CapableVmInnerAnnotatedFieldTypeTest {
    @Test
    void resolvesAnnotatedFieldTypeThroughLoadedTypeDescription() {
        TypeDescription typeDescription = TypeDescription.ForLoadedType.of(FieldTypeCarrier.class);

        FieldDescription fieldDescription = typeDescription.getDeclaredFields()
                .filter(named("markedField"))
                .getOnly();
        TypeDescription.Generic fieldType = fieldDescription.getType();

        assertThat(fieldType.asErasure().represents(String.class)).isTrue();
        assertThat(((java.util.function.Supplier<net.bytebuddy.description.annotation.AnnotationList>) fieldType::getDeclaredAnnotations)
                .get()
                .ofType(FieldTypeMarker.class)
                .getAnnotationType()
                .represents(FieldTypeMarker.class)).isTrue();
    }

    @Target(ElementType.TYPE_USE)
    @Retention(RetentionPolicy.RUNTIME)
    private @interface FieldTypeMarker {
    }

    private static class FieldTypeCarrier {
        private @FieldTypeMarker String markedField;
    }
}
