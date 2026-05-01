/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_smallrye_classfile.jdk_classfile_backport;

import java.lang.constant.ClassDesc;

import io.smallrye.classfile.ClassHierarchyResolver.ClassHierarchyInfo;
import io.smallrye.classfile.impl.ClassHierarchyImpl.ResourceParsingClassHierarchyResolver;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassHierarchyImplInnerResourceParsingClassHierarchyResolverAnonymous1Test {
    private static final ClassDesc ABSENT_CLASS = ClassDesc.of(
            "dynamic.access.coverage.missing",
            "AbsentSystemResourceParsingProbe");

    @Test
    void systemStreamProviderReportsMissingClassResourcesAsUnknownHierarchyInformation() {
        ResourceParsingClassHierarchyResolver resolver = new ResourceParsingClassHierarchyResolver(
                ResourceParsingClassHierarchyResolver.SYSTEM_STREAM_PROVIDER);

        ClassHierarchyInfo classInfo = resolver.getClassInfo(ABSENT_CLASS);

        assertThat(classInfo).isNull();
    }
}
