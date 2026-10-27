/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micronaut.micronaut_inject;

import static org.assertj.core.api.Assertions.assertThat;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.convert.DefaultMutableConversionService;
import io.micronaut.inject.annotation.AnnotationConvertersRegistrar;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(55)
public class AnnotationConvertersRegistrarTest {
    @Test
    void convertsAnnotationValuesToAnnotationInstancesAndArrays() {
        DefaultMutableConversionService conversionService = new DefaultMutableConversionService();
        new AnnotationConvertersRegistrar().register(conversionService);
        AnnotationValue<ConverterTag> first =
                AnnotationValue.builder(ConverterTag.class).member("value", "first").build();
        AnnotationValue<ConverterTag> second =
                AnnotationValue.builder(ConverterTag.class).member("value", "second").build();

        ConverterTag converted = conversionService.convertRequired(first, ConverterTag.class);
        ConverterTag[] convertedArray = conversionService.convertRequired(
                new AnnotationValue<?>[] {first, second}, ConverterTag[].class);

        assertThat(converted.value()).isEqualTo("first");
        assertThat(convertedArray).hasSize(2);
        assertThat(((ConverterTag) convertedArray[0]).value()).isEqualTo("first");
        assertThat(((ConverterTag) convertedArray[1]).value()).isEqualTo("second");
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface ConverterTag {
        String value();
    }
}
