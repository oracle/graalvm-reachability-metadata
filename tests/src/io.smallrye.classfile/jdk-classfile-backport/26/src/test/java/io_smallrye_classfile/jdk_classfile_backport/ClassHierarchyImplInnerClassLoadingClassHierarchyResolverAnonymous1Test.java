/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_smallrye_classfile.jdk_classfile_backport;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;

import io.smallrye.classfile.ClassHierarchyResolver;
import io.smallrye.classfile.ClassHierarchyResolver.ClassHierarchyInfo;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassHierarchyImplInnerClassLoadingClassHierarchyResolverAnonymous1Test {
    @Test
    void defaultResolverLoadsSystemClassHierarchyInformation() {
        ClassHierarchyResolver resolver = ClassHierarchyResolver.defaultResolver();

        ClassHierarchyInfo stringInfo = resolver.getClassInfo(ClassDesc.of("java.lang", "String"));
        ClassHierarchyInfo comparableInfo = resolver.getClassInfo(ClassDesc.of("java.lang", "Comparable"));

        assertThat(stringInfo).isEqualTo(ClassHierarchyInfo.ofClass(ConstantDescs.CD_Object));
        assertThat(comparableInfo).isEqualTo(ClassHierarchyInfo.ofInterface());
    }
}
