/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.lang.annotation.ElementType;

import org.junit.jupiter.api.Test;
import org.springframework.asm.ClassReader;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.type.classreading.AnnotationMetadataReadingVisitor;
import org.springframework.lang.NonNullApi;

/** Verifies legacy bytecode resolution of enum annotation values. */
@SuppressWarnings("deprecation")
public class AbstractRecursiveAnnotationVisitorTest {
    @Test
    void resolvesEnumConstantsWhileReadingMetadata() throws Exception {
        String path = NonNullApi.class.getName().replace('.', '/') + ".class";
        AnnotationMetadataReadingVisitor visitor =
                new AnnotationMetadataReadingVisitor(NonNullApi.class.getClassLoader());
        try (InputStream input = new ClassPathResource(path).getInputStream()) {
            new ClassReader(input).accept(visitor, ClassReader.SKIP_CODE);
        }

        AnnotationAttributes attributes = visitor.getAnnotationAttributes(
                "javax.annotation.meta.TypeQualifierDefault", false);

        assertThat(attributes).isNotNull();
        assertThat((ElementType[]) attributes.get("value")).contains(ElementType.METHOD);
    }
}
