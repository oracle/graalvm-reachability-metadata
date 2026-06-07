/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.description.type.TypeList;
import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static org.assertj.core.api.Assertions.assertThat;

public class TypeDescriptionInnerGenericInnerAnnotationReaderInnerDispatcherInnerForJava8CapableVmInnerAnnotatedInterfaceTypeTest {
    @Test
    void resolvesAnnotatedInterfaceTypeThroughLoadedTypeDescription() {
        TypeDescription typeDescription = TypeDescription.ForLoadedType.of(InterfaceTypeCarrier.class);

        TypeList.Generic interfaces = typeDescription.getInterfaces();
        TypeDescription.Generic interfaceType = interfaces.get(0);

        assertThat(interfaces).hasSize(1);
        assertThat(interfaceType.asErasure().represents(MarkedInterface.class)).isTrue();
        assertThat(((java.util.function.Supplier<net.bytebuddy.description.annotation.AnnotationList>) interfaceType::getDeclaredAnnotations)
                .get()
                .ofType(InterfaceTypeMarker.class)
                .getAnnotationType()
                .represents(InterfaceTypeMarker.class)).isTrue();
    }

    @Target(ElementType.TYPE_USE)
    @Retention(RetentionPolicy.RUNTIME)
    private @interface InterfaceTypeMarker {
    }

    private interface MarkedInterface {
    }

    private static class InterfaceTypeCarrier implements @InterfaceTypeMarker MarkedInterface {
    }
}
