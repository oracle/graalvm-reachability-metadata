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
import java.lang.constant.ConstantDescs;
import org.junit.jupiter.api.Test;

public class ClassHierarchyResolverAnonymous3Test {
    private static final ClassDesc RUNNABLE = ClassDesc.of("java.lang.Runnable");

    @Test
    void classLoadingResolverLoadsKnownPlatformClassesThroughProvidedLoader() {
        ClassLoader classLoader = ClassLoader.getSystemClassLoader();
        ClassHierarchyResolver resolver = ClassHierarchyResolver.ofClassLoading(classLoader);

        ClassHierarchyResolver.ClassHierarchyInfo stringInfo = resolver.getClassInfo(ConstantDescs.CD_String);
        ClassHierarchyResolver.ClassHierarchyInfo runnableInfo = resolver.getClassInfo(RUNNABLE);

        assertThat(stringInfo).isEqualTo(ClassHierarchyResolver.ClassHierarchyInfo.ofClass(ConstantDescs.CD_Object));
        assertThat(runnableInfo).isEqualTo(ClassHierarchyResolver.ClassHierarchyInfo.ofInterface());
    }
}
