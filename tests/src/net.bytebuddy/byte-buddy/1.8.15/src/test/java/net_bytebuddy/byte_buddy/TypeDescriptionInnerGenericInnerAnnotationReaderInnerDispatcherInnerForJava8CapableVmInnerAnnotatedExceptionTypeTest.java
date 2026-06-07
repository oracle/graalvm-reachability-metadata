/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.description.type.TypeList;
import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

public class TypeDescriptionInnerGenericInnerAnnotationReaderInnerDispatcherInnerForJava8CapableVmInnerAnnotatedExceptionTypeTest {
    @Test
    void resolvesAnnotatedMethodExceptionTypeThroughLoadedMethodDescription() throws Exception {
        Method method = ExceptionTypeCarrier.class.getDeclaredMethod("failWithMarkedException");

        TypeList.Generic exceptionTypes = new MethodDescription.ForLoadedMethod(method).getExceptionTypes();
        TypeDescription.Generic exceptionType = exceptionTypes.get(0);

        assertThat(exceptionTypes).hasSize(1);
        assertThat(exceptionType.asErasure().represents(MarkedException.class)).isTrue();
        assertThat(exceptionType.getDeclaredAnnotations().isAnnotationPresent(ExceptionTypeMarker.class)).isTrue();
    }

    @Target(ElementType.TYPE_USE)
    @Retention(RetentionPolicy.RUNTIME)
    private @interface ExceptionTypeMarker {
    }

    private static class MarkedException extends Exception {
    }

    private static class ExceptionTypeCarrier {
        void failWithMarkedException() throws @ExceptionTypeMarker MarkedException {
        }
    }
}
