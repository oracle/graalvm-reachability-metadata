/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.mchange_commons_java;

import java.lang.reflect.Method;
import java.util.ArrayList;

import com.mchange.v1.lang.ClassUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassUtilsTest {
    @Test
    void containsMethodAsSubtypeFindsMethodWithCovariantReturnType() throws Exception {
        Method method = NameSupplier.class.getMethod("name");

        boolean containsMethod = ClassUtils.containsMethodAsSubtype(StringNameSupplier.class, method);

        assertThat(containsMethod).isTrue();
    }

    @Test
    void forNameResolvesFullyQualifiedClassNames() throws Exception {
        Class<?> resolved = ClassUtils.forName("java.lang.Integer");

        assertThat(resolved).isEqualTo(Integer.class);
    }

    @Test
    void overloadedForNameResolvesFullyQualifiedClassNamesBeforeCheckingImports() throws Exception {
        Class<?> resolved = ClassUtils.forName(
            "java.lang.String",
            new String[] { "java.util" },
            new String[] { ArrayList.class.getName() }
        );

        assertThat(resolved).isEqualTo(String.class);
    }

    @Test
    void classForSimpleNameResolvesImportedClassesJavaLangTypesAndImportedPackages() throws Exception {
        Class<?> importedClass = ClassUtils.classForSimpleName(
            "ClassUtilsTest",
            null,
            new String[] { ClassUtilsTest.class.getName() }
        );
        Class<?> javaLangClass = ClassUtils.classForSimpleName("String", null, null);
        Class<?> importedPackageClass = ClassUtils.classForSimpleName(
            "ArrayList",
            new String[] { "java.util" },
            null
        );

        assertThat(importedClass).isEqualTo(ClassUtilsTest.class);
        assertThat(javaLangClass).isEqualTo(String.class);
        assertThat(importedPackageClass).isEqualTo(ArrayList.class);
    }

    public interface NameSupplier {
        CharSequence name();
    }

    public static final class StringNameSupplier implements NameSupplier {
        @Override
        public String name() {
            return "value";
        }
    }
}
