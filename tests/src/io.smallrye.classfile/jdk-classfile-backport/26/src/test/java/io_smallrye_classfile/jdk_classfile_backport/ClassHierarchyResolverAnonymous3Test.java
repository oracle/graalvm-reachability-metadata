/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_smallrye_classfile.jdk_classfile_backport;

import java.lang.constant.ClassDesc;

import io.smallrye.classfile.ClassHierarchyResolver;
import io.smallrye.classfile.ClassHierarchyResolver.ClassHierarchyInfo;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassHierarchyResolverAnonymous3Test {
    private static final String PACKAGE_NAME = ClassHierarchyResolverAnonymous3Test.class.getPackageName();
    private static final String CHILD_CLASS_NAME = "ClassHierarchyResolverAnonymous3Child";
    private static final String PARENT_CLASS_NAME = "ClassHierarchyResolverAnonymous3Parent";
    private static final String INTERFACE_CLASS_NAME = "ClassHierarchyResolverAnonymous3Interface";
    private static final String MISSING_CLASS_NAME = "ClassHierarchyResolverAnonymous3Missing";

    @Test
    void resolvesHierarchyInformationByLoadingClassesThroughProvidedClassLoader() {
        ClassLoader loader = ClassHierarchyResolverAnonymous3Test.class.getClassLoader();
        ClassHierarchyResolver resolver = ClassHierarchyResolver.ofClassLoading(loader);

        ClassHierarchyInfo childInfo = resolver.getClassInfo(testClassDesc(CHILD_CLASS_NAME));
        ClassHierarchyInfo interfaceInfo = resolver.getClassInfo(testClassDesc(INTERFACE_CLASS_NAME));

        assertThat(childInfo).isEqualTo(ClassHierarchyInfo.ofClass(testClassDesc(PARENT_CLASS_NAME)));
        assertThat(interfaceInfo).isEqualTo(ClassHierarchyInfo.ofInterface());
    }

    @Test
    void returnsNullWhenProvidedClassLoaderCannotFindClass() {
        ClassLoader loader = ClassHierarchyResolverAnonymous3Test.class.getClassLoader();
        ClassHierarchyResolver resolver = ClassHierarchyResolver.ofClassLoading(loader);

        ClassHierarchyInfo missingInfo = resolver.getClassInfo(testClassDesc(MISSING_CLASS_NAME));

        assertThat(missingInfo).isNull();
    }

    private static ClassDesc testClassDesc(String simpleName) {
        return ClassDesc.of(PACKAGE_NAME, simpleName);
    }
}

class ClassHierarchyResolverAnonymous3Parent {
}

class ClassHierarchyResolverAnonymous3Child extends ClassHierarchyResolverAnonymous3Parent {
}

interface ClassHierarchyResolverAnonymous3Interface {
}
