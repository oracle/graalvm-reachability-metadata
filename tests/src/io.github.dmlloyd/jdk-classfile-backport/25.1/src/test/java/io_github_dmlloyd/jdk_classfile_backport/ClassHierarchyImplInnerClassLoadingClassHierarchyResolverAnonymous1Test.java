/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_github_dmlloyd.jdk_classfile_backport;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.dmlloyd.classfile.ClassHierarchyResolver;
import java.lang.constant.ClassDesc;
import org.junit.jupiter.api.Test;

public class ClassHierarchyImplInnerClassLoadingClassHierarchyResolverAnonymous1Test {
    private static final ClassDesc ARRAY_LIST = ClassDesc.of("java.util.ArrayList");
    private static final ClassDesc ABSTRACT_LIST = ClassDesc.of("java.util.AbstractList");
    private static final ClassDesc LIST = ClassDesc.of("java.util.List");

    @Test
    void defaultResolverLoadsSystemClassesThroughSystemClassProvider() {
        ClassHierarchyResolver resolver = ClassHierarchyResolver.defaultResolver();

        ClassHierarchyResolver.ClassHierarchyInfo classInfo = resolver.getClassInfo(ARRAY_LIST);
        ClassHierarchyResolver.ClassHierarchyInfo interfaceInfo = resolver.getClassInfo(LIST);

        assertThat(classInfo).isEqualTo(ClassHierarchyResolver.ClassHierarchyInfo.ofClass(ABSTRACT_LIST));
        assertThat(interfaceInfo).isEqualTo(ClassHierarchyResolver.ClassHierarchyInfo.ofInterface());
    }
}
