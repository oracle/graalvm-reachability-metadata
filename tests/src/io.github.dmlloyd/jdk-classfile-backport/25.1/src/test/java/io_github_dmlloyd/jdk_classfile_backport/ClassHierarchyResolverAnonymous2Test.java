/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_github_dmlloyd.jdk_classfile_backport;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.dmlloyd.classfile.ClassFile;
import io.github.dmlloyd.classfile.ClassHierarchyResolver;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class ClassHierarchyResolverAnonymous2Test {
    private static final ClassDesc RESOURCE_PARSED_CLASS = ClassDesc.of("coverage.ResourceParsedClass");
    private static final String RESOURCE_PARSED_CLASS_FILE = "coverage/ResourceParsedClass.class";

    @Test
    void resourceParsingResolverUsesProvidedClassLoaderToReadClassBytes() {
        byte[] classBytes = ClassFile.of().build(RESOURCE_PARSED_CLASS,
                classBuilder -> classBuilder.withFlags(ClassFile.ACC_PUBLIC));
        RecordingClassLoader classLoader = new RecordingClassLoader(Map.of(RESOURCE_PARSED_CLASS_FILE, classBytes));
        ClassHierarchyResolver resolver = ClassHierarchyResolver.ofResourceParsing(classLoader);

        ClassHierarchyResolver.ClassHierarchyInfo info = resolver.getClassInfo(RESOURCE_PARSED_CLASS);

        assertThat(info).isEqualTo(ClassHierarchyResolver.ClassHierarchyInfo.ofClass(ConstantDescs.CD_Object));
        assertThat(classLoader.requestCounts()).containsEntry(RESOURCE_PARSED_CLASS_FILE, 1);
    }

    @Test
    void resourceParsingResolverReturnsNullWhenClassLoaderCannotFindClassFile() {
        RecordingClassLoader classLoader = new RecordingClassLoader(Map.of());
        ClassHierarchyResolver resolver = ClassHierarchyResolver.ofResourceParsing(classLoader);
        ClassDesc missingClass = ClassDesc.of("coverage.MissingResourceParsedClass");

        ClassHierarchyResolver.ClassHierarchyInfo info = resolver.getClassInfo(missingClass);

        assertThat(info).isNull();
        assertThat(classLoader.requestCounts()).containsEntry("coverage/MissingResourceParsedClass.class", 1);
    }

    private static final class RecordingClassLoader extends ClassLoader {
        private final Map<String, byte[]> resources;
        private final Map<String, Integer> requestCounts = new HashMap<>();

        private RecordingClassLoader(Map<String, byte[]> resources) {
            super(null);
            this.resources = Map.copyOf(resources);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            requestCounts.merge(name, 1, Integer::sum);
            byte[] resourceBytes = resources.get(name);
            return resourceBytes == null ? null : new ByteArrayInputStream(resourceBytes);
        }

        private Map<String, Integer> requestCounts() {
            return requestCounts;
        }
    }
}
