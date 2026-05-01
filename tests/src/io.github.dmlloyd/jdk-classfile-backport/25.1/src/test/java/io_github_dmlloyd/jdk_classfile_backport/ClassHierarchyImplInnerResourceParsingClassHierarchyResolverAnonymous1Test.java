/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_github_dmlloyd.jdk_classfile_backport;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.dmlloyd.classfile.ClassHierarchyResolver;
import io.github.dmlloyd.classfile.impl.ClassHierarchyImpl.ResourceParsingClassHierarchyResolver;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import org.junit.jupiter.api.Test;

public class ClassHierarchyImplInnerResourceParsingClassHierarchyResolverAnonymous1Test {
    private static final ClassDesc RESOURCE_CLASS = ClassDesc.of("coverage.SystemResourceProviderTarget");

    @Test
    void systemStreamProviderSuppliesClassBytesForResourceParsingResolver() {
        ClassHierarchyResolver resolver = ClassHierarchyResolver.ofResourceParsing(
                ResourceParsingClassHierarchyResolver.SYSTEM_STREAM_PROVIDER);

        ClassHierarchyResolver.ClassHierarchyInfo info = resolver.getClassInfo(RESOURCE_CLASS);

        assertThat(info).isEqualTo(ClassHierarchyResolver.ClassHierarchyInfo.ofClass(ConstantDescs.CD_Object));
    }
}
