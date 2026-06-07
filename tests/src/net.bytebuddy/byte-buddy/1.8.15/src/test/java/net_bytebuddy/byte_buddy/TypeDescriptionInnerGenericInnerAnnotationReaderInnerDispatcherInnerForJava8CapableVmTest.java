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

public class TypeDescriptionInnerGenericInnerAnnotationReaderInnerDispatcherInnerForJava8CapableVmTest {
    @Test
    void resolvesAnnotatedMethodReceiverTypeThroughLoadedMethodDescription() throws Exception {
        Method method = ReceiverTypeCarrier.class.getDeclaredMethod("describe");

        TypeDescription.Generic receiverType = new MethodDescription.ForLoadedMethod(method).getReceiverType();

        assertThat(receiverType).isNotNull();
        assertThat(receiverType.asErasure().represents(ReceiverTypeCarrier.class)).isTrue();
        assertThat(((java.util.function.Supplier<net.bytebuddy.description.annotation.AnnotationList>) receiverType::getDeclaredAnnotations)
                .get()
                .ofType(ReceiverMarker.class)
                .getAnnotationType()
                .represents(ReceiverMarker.class)).isTrue();
    }

    @Target(ElementType.TYPE_USE)
    @Retention(RetentionPolicy.RUNTIME)
    private @interface ReceiverMarker {
    }

    private static class ReceiverTypeCarrier {
        void describe(@ReceiverMarker ReceiverTypeCarrier this) {
        }
    }
}
