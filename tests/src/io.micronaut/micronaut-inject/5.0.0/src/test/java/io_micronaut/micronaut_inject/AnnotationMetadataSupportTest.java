/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micronaut.micronaut_inject;

import static org.assertj.core.api.Assertions.assertThat;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.annotation.AnnotationMetadataSupport;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(55)
public class AnnotationMetadataSupportTest {
    @Test
    void buildsAnAnnotationProxyFromAnnotationMetadata() {
        AnnotationValue<SupportedTag> value =
                AnnotationValue.builder(SupportedTag.class)
                        .member("value", "supported")
                        .build();

        SupportedTag annotation =
                AnnotationMetadataSupport.buildAnnotation(SupportedTag.class, value);

        assertThat(annotation.annotationType()).isEqualTo(SupportedTag.class);
        assertThat(annotation.value()).isEqualTo("supported");
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface SupportedTag {
        String value();
    }
}
