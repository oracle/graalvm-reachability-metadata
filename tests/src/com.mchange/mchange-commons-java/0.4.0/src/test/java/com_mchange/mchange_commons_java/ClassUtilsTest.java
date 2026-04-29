/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.mchange_commons_java;

import com.mchange.v1.lang.AmbiguousClassNameException;
import com.mchange.v1.lang.ClassUtils;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassUtilsTest {
    @Test
    void containsMethodAsSupertypeFindsMatchingInterfaceMethod() throws NoSuchMethodException {
        Method iteratorMethod = Collection.class.getMethod("iterator");

        boolean containsMethod = ClassUtils.containsMethodAsSupertype(Iterable.class, iteratorMethod);

        assertThat(containsMethod).isTrue();
    }

    @Test
    void forNameResolvesPrimitiveAndFullyQualifiedClassNames() throws ClassNotFoundException {
        assertThat(ClassUtils.forName("int")).isSameAs(int.class);
        assertThat(ClassUtils.forName("java.util.ArrayList")).isSameAs(ArrayList.class);
    }

    @Test
    void forNameWithImportsReturnsFullyQualifiedClassBeforeTryingImports()
            throws AmbiguousClassNameException, ClassNotFoundException {
        Class<?> resolvedClass = ClassUtils.forName(
                "java.util.Map",
                new String[]{"java.sql"},
                new String[]{"java.util.List"});

        assertThat(resolvedClass).isSameAs(Map.class);
    }

    @Test
    void classForSimpleNameResolvesExplicitImportedClass()
            throws AmbiguousClassNameException, ClassNotFoundException {
        Class<?> resolvedClass = ClassUtils.classForSimpleName("Map", null, new String[]{"java.util.Map"});

        assertThat(resolvedClass).isSameAs(Map.class);
    }

    @Test
    void classForSimpleNameResolvesJavaLangClassBeforeImportedPackages()
            throws AmbiguousClassNameException, ClassNotFoundException {
        Class<?> resolvedClass = ClassUtils.classForSimpleName("String", new String[]{"java.util"}, null);

        assertThat(resolvedClass).isSameAs(String.class);
    }

    @Test
    void classForSimpleNameResolvesClassFromImportedPackage()
            throws AmbiguousClassNameException, ClassNotFoundException {
        Class<?> resolvedClass = ClassUtils.classForSimpleName("Date", new String[]{"java.util"}, null);

        assertThat(resolvedClass).isSameAs(Date.class);
    }
}
