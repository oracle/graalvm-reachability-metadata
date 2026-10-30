/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.apiguardian.api.API;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.SimpleMetadataReaderFactory;

/** Verifies annotation metadata resolution of enum constants. */
public class AbstractRecursiveAnnotationVisitorTest {
    @Test
    void resolvesEnumConstantWhileReadingDependencyClassMetadata() throws Exception {
        MetadataReader reader = new SimpleMetadataReaderFactory(getClass().getClassLoader())
                .getMetadataReader(Test.class.getName());

        AnnotationAttributes attributes = AnnotationAttributes.fromMap(
                reader.getAnnotationMetadata().getAnnotationAttributes(API.class.getName(), false));

        assertThat(attributes).isNotNull();
        API.Status status = attributes.getEnum("status");
        assertThat(status).isEqualTo(API.Status.STABLE);
    }
}
