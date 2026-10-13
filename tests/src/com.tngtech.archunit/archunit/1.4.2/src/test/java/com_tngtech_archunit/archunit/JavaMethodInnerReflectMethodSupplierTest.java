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
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

public class JavaMethodInnerReflectMethodSupplierTest {
    @Test
    void reflectsAnImportedMethodWithItsParameterTypes() {
        JavaClass importedClass = new ClassFileImporter().importClass(AnnotatedFixture.class);

        Method method = importedClass.getMethod("greet", String.class).reflect();

        assertThat(method.getDeclaringClass()).isEqualTo(AnnotatedFixture.class);
        assertThat(method.getParameterTypes()).containsExactly(String.class);
        assertThat(method.getReturnType()).isEqualTo(String.class);
    }
}
