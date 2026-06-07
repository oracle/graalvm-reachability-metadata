/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.ParameterDescription;
import net.bytebuddy.description.method.ParameterList;
import net.bytebuddy.description.type.TypeDescription;
import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

public class TypeDescriptionInnerGenericInnerAnnotationReaderInnerDispatcherInnerForJava8CapableVmInnerAnnotatedParameterizedTypeTest {
    @Test
    void resolvesAnnotatedMethodParameterTypeThroughLoadedMethodDescription() throws Exception {
        Method method = ParameterTypeCarrier.class.getDeclaredMethod("describe", String.class, int.class);

        ParameterList<ParameterDescription.InDefinedShape> parameters = new MethodDescription.ForLoadedMethod(method)
                .getParameters();
        TypeDescription.Generic parameterType = parameters.get(0).getType();

        assertThat(parameters).hasSize(2);
        assertThat(parameterType.asErasure().represents(String.class)).isTrue();
        assertThat(((java.util.function.Supplier<net.bytebuddy.description.annotation.AnnotationList>) parameterType::getDeclaredAnnotations)
                .get()
                .ofType(ParameterTypeMarker.class)
                .getAnnotationType()
                .represents(ParameterTypeMarker.class)).isTrue();
    }

    @Target(ElementType.TYPE_USE)
    @Retention(RetentionPolicy.RUNTIME)
    private @interface ParameterTypeMarker {
    }

    private static class ParameterTypeCarrier {
        String describe(@ParameterTypeMarker String label, int count) {
            return label + count;
        }
    }
}
