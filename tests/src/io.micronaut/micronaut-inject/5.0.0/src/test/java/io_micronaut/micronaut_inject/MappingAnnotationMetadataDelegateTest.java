/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micronaut.micronaut_inject;

import static org.assertj.core.api.Assertions.assertThat;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.expressions.EvaluatedExpressionReference;
import io.micronaut.inject.annotation.EvaluatedAnnotationMetadata;
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
public class MappingAnnotationMetadataDelegateTest {
    @Test
    void mapsEnumValuesAndSynthesizesMappedAnnotations() {
        MutableAnnotationMetadata emptyMetadata = new MutableAnnotationMetadata();
        markEvaluated(emptyMetadata);
        AnnotationMetadata emptyDelegate =
                EvaluatedAnnotationMetadata.wrapIfNecessary(emptyMetadata);
        assertThat(
                        emptyDelegate.enumValues(
                                MappingTag.class,
                                "value",
                                DefaultAnnotationMetadataTest.Level.class))
                .isEmpty();

        MutableAnnotationMetadata metadata = new MutableAnnotationMetadata();
        metadata.addRepeatable(
                MappingTag.class.getName(),
                AnnotationValue.builder(MappingTag.class)
                        .member("value", DefaultAnnotationMetadataTest.Level.HIGH)
                        .build());
        markEvaluated(metadata);
        AnnotationMetadata delegate = EvaluatedAnnotationMetadata.wrapIfNecessary(metadata);

        assertThat(delegate.synthesizeAnnotationsByType(MappingTag.class))
                .extracting(MappingTag::value)
                .containsExactly(DefaultAnnotationMetadataTest.Level.HIGH);
        assertThat(delegate.synthesizeDeclaredAnnotationsByType(MappingTag.class)).isEmpty();
    }

    private static void markEvaluated(MutableAnnotationMetadata metadata) {
        metadata.addAnnotation(
                "test.ExpressionMarker",
                Map.of(
                        "value",
                        new EvaluatedExpressionReference(
                                "value", "test.ExpressionMarker", "value", "test.Expression")));
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @Repeatable(MappingTags.class)
    public @interface MappingTag {
        DefaultAnnotationMetadataTest.Level value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface MappingTags {
        MappingTag[] value();
    }
}
