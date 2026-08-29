/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.ElementType;

import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.classreading.SimpleMetadataReaderFactory;
import org.springframework.lang.NonNullApi;

/** Verifies typed annotation arrays read from library class bytecode. */
public class MergedAnnotationReadingVisitorInnerArrayVisitorTest {
    @Test
    @SuppressWarnings("annotationAccess")
    void readsEnumArrayFromClassMetadata() throws Exception {
        AnnotationMetadata metadata = new SimpleMetadataReaderFactory()
                .getMetadataReader(NonNullApi.class.getName())
                .getAnnotationMetadata();
        MergedAnnotation<?> annotation =
                metadata.getAnnotations().get("javax.annotation.meta.TypeQualifierDefault");

        assertThat(annotation.getEnumArray("value", ElementType.class))
                .containsExactly(ElementType.METHOD, ElementType.PARAMETER);
    }
}
