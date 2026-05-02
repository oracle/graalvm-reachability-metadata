/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_smallrye_classfile.jdk_classfile_backport;

import static java.lang.constant.ConstantDescs.CD_String;
import static org.assertj.core.api.Assertions.assertThat;

import io.smallrye.classfile.ClassHierarchyResolver;
import org.junit.jupiter.api.Test;

public class ClassHierarchyImplInnerClassLoadingClassHierarchyResolverAnonymous1Test {
    @Test
    void defaultResolverLoadsKnownClassFromSystemClassLoader() {
        ClassHierarchyResolver resolver = ClassHierarchyResolver.defaultResolver();

        ClassHierarchyResolver.ClassHierarchyInfo classInfo = resolver.getClassInfo(CD_String);

        assertThat(classInfo).isNotNull();
    }
}
