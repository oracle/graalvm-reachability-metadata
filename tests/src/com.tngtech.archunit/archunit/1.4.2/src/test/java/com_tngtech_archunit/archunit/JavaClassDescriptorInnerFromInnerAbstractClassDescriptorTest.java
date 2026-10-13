/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_tngtech_archunit.archunit;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

public class JavaClassDescriptorInnerFromInnerAbstractClassDescriptorTest {
    @Test
    void reflectsAnImportedClassThroughItsDomainDescriptor() {
        JavaClass importedClass = new ClassFileImporter().importClass(AnnotatedFixture.class);

        Class<?> reflectedClass = importedClass.reflect();

        assertThat(reflectedClass).isSameAs(AnnotatedFixture.class);
        assertThat(reflectedClass.getDeclaredAnnotation(ImportedAnnotation.class)).isNotNull();
    }
}
