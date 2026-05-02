/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_smallrye_classfile.jdk_classfile_backport;

import static org.assertj.core.api.Assertions.assertThat;

import io.smallrye.classfile.ClassHierarchyResolver;
import io.smallrye.classfile.impl.ClassHierarchyImpl;
import java.lang.constant.ClassDesc;
import org.junit.jupiter.api.Test;

public class ClassHierarchyImplInnerResourceParsingClassHierarchyResolverAnonymous1Test {
    private static final ClassDesc CLASS_HIERARCHY_RESOLVER = ClassDesc.of(
            "io.smallrye.classfile.ClassHierarchyResolver");

    @Test
    void systemResourceProviderFindsAndParsesClassFileResource() {
        ClassHierarchyResolver resolver = new ClassHierarchyImpl.ResourceParsingClassHierarchyResolver(
                ClassHierarchyImpl.ResourceParsingClassHierarchyResolver.SYSTEM_STREAM_PROVIDER);

        ClassHierarchyResolver.ClassHierarchyInfo classInfo = resolver.getClassInfo(CLASS_HIERARCHY_RESOLVER);

        assertThat(classInfo).isNotNull();
    }
}
