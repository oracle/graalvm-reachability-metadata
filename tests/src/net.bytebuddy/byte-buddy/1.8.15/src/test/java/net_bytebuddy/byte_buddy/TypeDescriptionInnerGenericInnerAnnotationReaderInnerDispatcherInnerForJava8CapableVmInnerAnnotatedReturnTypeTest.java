/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

public class TypeDescriptionInnerGenericInnerAnnotationReaderInnerDispatcherInnerForJava8CapableVmInnerAnnotatedReturnTypeTest {
    @Test
    void resolvesAnnotatedMethodReturnTypeThroughLoadedMethodDescription() throws Exception {
        Method method = ReturnTypeCarrier.class.getDeclaredMethod("describe");

        TypeDescription.Generic returnType = new MethodDescription.ForLoadedMethod(method).getReturnType();

        assertThat(returnType.asErasure().represents(String.class)).isTrue();
        assertThat(returnType.getDeclaredAnnotations().isAnnotationPresent(ReturnTypeMarker.class)).isTrue();
    }

    @Target(ElementType.TYPE_USE)
    @Retention(RetentionPolicy.RUNTIME)
    private @interface ReturnTypeMarker {
    }

    private static class ReturnTypeCarrier {
        @ReturnTypeMarker String describe() {
            return "marked";
        }
    }
}
