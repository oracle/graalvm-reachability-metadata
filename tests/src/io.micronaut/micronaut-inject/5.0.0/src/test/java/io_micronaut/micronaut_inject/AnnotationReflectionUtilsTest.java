/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micronaut.micronaut_inject;

import static org.assertj.core.api.Assertions.assertThat;

import io.micronaut.context.AnnotationReflectionUtils;
import io.micronaut.core.type.Argument;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(55)
public class AnnotationReflectionUtilsTest {
    @Test
    void resolvesAnnotatedConcreteAndGenericArrayArguments() {
        Argument<GenericContract> concrete =
                AnnotationReflectionUtils.resolveGenericToArgument(
                        ArrayImplementation.class, GenericContract.class);
        Argument<GenericContract> generic =
                AnnotationReflectionUtils.resolveGenericToArgument(
                        GenericArrayImplementation.class, GenericContract.class);

        assertThat(concrete.getType()).isEqualTo(GenericContract.class);
        assertThat(concrete.getFirstTypeVariable().orElseThrow().getType())
                .isEqualTo(String[].class);
        assertThat(generic.getType()).isEqualTo(GenericContract.class);
        assertThat(generic.getFirstTypeVariable().orElseThrow().getType())
                .isEqualTo(Object[].class);
    }

    @Test
    void resolvesGenericArrayArgumentThroughAnInheritedContract() {
        Argument<GenericContract> argument =
                AnnotationReflectionUtils.resolveGenericToArgument(
                        InheritedArrayImplementation.class, GenericContract.class);

        assertThat(argument.getType()).isEqualTo(GenericContract.class);
        assertThat(argument.getFirstTypeVariable().orElseThrow().getType())
                .isEqualTo(String[].class);
    }

    interface GenericContract<T> {}

    interface GenericArrayContract<T> extends GenericContract<T[]> {}

    static final class ArrayImplementation
            implements GenericContract<@TypeUse String @TypeUse []> {}

    static final class GenericArrayImplementation<T> implements GenericContract<T[]> {}

    static final class InheritedArrayImplementation implements GenericArrayContract<String> {}

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE_USE)
    @interface TypeUse {}
}
