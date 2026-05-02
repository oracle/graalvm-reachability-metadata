/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_smallrye_classfile.jdk_classfile_backport;

import static org.assertj.core.api.Assertions.assertThat;

import io.smallrye.classfile.ClassHierarchyResolver;
import java.lang.constant.ClassDesc;
import java.net.URL;
import org.junit.jupiter.api.Test;

public class ClassHierarchyResolverAnonymous2Test {
    private static final ClassDesc MISSING_CLASS = ClassDesc.of("coverage.classhierarchy.DoesNotExist");

    @Test
    void resourceParsingResolverQueriesProvidedClassLoader() {
        RecordingClassLoader classLoader = new RecordingClassLoader();
        ClassHierarchyResolver resolver = ClassHierarchyResolver.ofResourceParsing(classLoader);

        ClassHierarchyResolver.ClassHierarchyInfo classInfo = resolver.getClassInfo(MISSING_CLASS);

        assertThat(classInfo).isNull();
        assertThat(classLoader.requestedResource()).isEqualTo("coverage/classhierarchy/DoesNotExist.class");
    }

    private static final class RecordingClassLoader extends ClassLoader {
        private String requestedResource;

        @Override
        public URL getResource(String name) {
            requestedResource = name;
            return null;
        }

        String requestedResource() {
            return requestedResource;
        }
    }
}
