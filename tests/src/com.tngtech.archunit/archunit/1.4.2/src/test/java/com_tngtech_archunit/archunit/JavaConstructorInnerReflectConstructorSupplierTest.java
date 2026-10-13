/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_tngtech_archunit.archunit;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import java.lang.reflect.Constructor;
import org.junit.jupiter.api.Test;

public class JavaConstructorInnerReflectConstructorSupplierTest {
    @Test
    void reflectsAnImportedConstructorFromItsDomainModel() {
        JavaClass importedClass = TestClassImporter.importClass(AnnotatedFixture.class);

        Constructor<?> constructor = importedClass.getConstructor().reflect();

        assertThat(constructor.getDeclaringClass()).isEqualTo(AnnotatedFixture.class);
        assertThat(constructor.getParameterCount()).isZero();
    }
}
