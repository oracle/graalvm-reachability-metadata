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
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.asm.ClassReader;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.classreading.AnnotationMetadataReadingVisitor;

/** Verifies legacy recursive reading of a populated annotation array. */
@SuppressWarnings("deprecation")
public class RecursiveAnnotationArrayVisitorTest {
    @Test
    void readsPopulatedClassArrayFromDependencyClass() throws Exception {
        AnnotationMetadataReadingVisitor visitor =
                new AnnotationMetadataReadingVisitor(getClass().getClassLoader());
        new ClassReader(DisabledOnOs.class.getName()).accept(visitor, ClassReader.SKIP_CODE);

        AnnotationAttributes attributes =
                visitor.getAnnotationAttributes(ExtendWith.class.getName(), false);

        assertThat(attributes).isNotNull();
        assertThat((Object[]) attributes.get("value")).hasSize(1);
    }
}
