/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micronaut.micronaut_inject;

import static org.assertj.core.api.Assertions.assertThat;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.annotation.DefaultAnnotationMetadata;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(55)
public class DefaultAnnotationMetadataTest {
    @Test
    void synthesizesRepeatedAnnotationsAndConvertsEnumArrays() {
        DefaultAnnotationMetadata.registerRepeatableAnnotations(
                Map.of(Tag.class.getName(), Tags.class.getName()));
        MutableAnnotationMetadata emptyMetadata = new MutableAnnotationMetadata();
        assertThat(emptyMetadata.enumValues(Tag.class, "value", Level.class)).isEmpty();

        MutableAnnotationMetadata metadata = new MutableAnnotationMetadata();
        metadata.addRepeatable(
                Tag.class.getName(),
                AnnotationValue.builder(Tag.class).member("value", Level.HIGH).build());
        metadata.addDeclaredRepeatable(
                Tag.class.getName(),
                AnnotationValue.builder(Tag.class).member("value", Level.LOW).build());

        assertThat(metadata.synthesizeAnnotationsByType(Tag.class))
                .extracting(Tag::value)
                .containsExactly(Level.HIGH, Level.LOW);
        assertThat(metadata.synthesizeDeclaredAnnotationsByType(Tag.class))
                .extracting(Tag::value)
                .containsExactly(Level.HIGH, Level.LOW);
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @Repeatable(Tags.class)
    public @interface Tag {
        Level value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface Tags {
        Tag[] value();
    }

    public enum Level {
        HIGH,
        LOW
    }
}
