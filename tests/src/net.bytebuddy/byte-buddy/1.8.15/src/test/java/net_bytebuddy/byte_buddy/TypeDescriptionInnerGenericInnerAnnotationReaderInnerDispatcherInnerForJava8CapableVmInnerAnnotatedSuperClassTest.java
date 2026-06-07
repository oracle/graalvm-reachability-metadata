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

public class TypeDescriptionInnerGenericInnerAnnotationReaderInnerDispatcherInnerForJava8CapableVmInnerAnnotatedSuperClassTest {
    @Test
    void resolvesAnnotatedSuperClassThroughLoadedTypeDescription() {
        TypeDescription typeDescription = TypeDescription.ForLoadedType.of(SuperClassCarrier.class);

        TypeDescription.Generic superClass = typeDescription.getSuperClass();

        assertThat(superClass.asErasure().represents(MarkedSuperClass.class)).isTrue();
        assertThat(((java.util.function.Supplier<net.bytebuddy.description.annotation.AnnotationList>) superClass::getDeclaredAnnotations)
                .get()
                .ofType(SuperClassMarker.class)
                .getAnnotationType()
                .represents(SuperClassMarker.class)).isTrue();
    }

    @Target(ElementType.TYPE_USE)
    @Retention(RetentionPolicy.RUNTIME)
    private @interface SuperClassMarker {
    }

    private static class MarkedSuperClass {
    }

    private static class SuperClassCarrier extends @SuperClassMarker MarkedSuperClass {
    }
}
