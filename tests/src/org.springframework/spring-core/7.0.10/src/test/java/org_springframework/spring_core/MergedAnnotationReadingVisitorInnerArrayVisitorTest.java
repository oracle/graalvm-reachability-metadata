/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.SimpleMetadataReaderFactory;

/** Verifies typed annotation-array materialization during ASM metadata reading. */
public class MergedAnnotationReadingVisitorInnerArrayVisitorTest {
    @Test
    void readsTypedClassArrayFromDependencyClass() throws Exception {
        MetadataReader reader = new SimpleMetadataReaderFactory(getClass().getClassLoader())
                .getMetadataReader(DisabledOnOs.class.getName());
        MergedAnnotation<ExtendWith> annotation =
                reader.getAnnotationMetadata().getAnnotations().get(ExtendWith.class);

        Class<?>[] extensionTypes = annotation.getClassArray("value");

        assertThat(extensionTypes).hasSize(1);
        assertThat(ExecutionCondition.class).isAssignableFrom(extensionTypes[0]);
    }
}
