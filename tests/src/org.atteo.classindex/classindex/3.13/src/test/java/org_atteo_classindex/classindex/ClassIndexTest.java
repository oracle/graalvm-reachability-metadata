/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_atteo_classindex.classindex;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.atteo.classindex.ClassIndex;
import org.junit.jupiter.api.Test;

public class ClassIndexTest {
    private static final String PACKAGE_NAME = ClassIndexTest.class.getPackage().getName();

    private final ClassLoader classLoader = ClassIndexTest.class.getClassLoader();

    @Test
    void findsClassesFromSubclassIndex() {
        Iterable<Class<? extends ClassIndexTest>> subclasses = ClassIndex.getSubclasses(
                ClassIndexTest.class, classLoader);

        assertThat(classNames(subclasses)).contains(ClassIndexTest.class.getName());
    }

    @Test
    void findsPackageClassesFromJaxbIndex() {
        Iterable<Class<?>> packageClasses = ClassIndex.getPackageClasses(PACKAGE_NAME, classLoader);

        assertThat(classNames(packageClasses)).contains(ClassIndexTest.class.getName());
    }

    @Test
    void readsJavadocSummaryResource() {
        String summary = ClassIndex.getClassSummary(ClassIndexTest.class, classLoader);

        assertThat(summary).isEqualTo("Summary sentence");
    }

    private static List<String> classNames(Iterable<? extends Class<?>> classes) {
        List<String> names = new ArrayList<>();
        for (Class<?> klass : classes) {
            names.add(klass.getName());
        }
        return names;
    }
}
