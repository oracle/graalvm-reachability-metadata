/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_smallrye_classfile.jdk_classfile_backport;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.constant.ClassDesc;

import io.smallrye.classfile.ClassFile;
import io.smallrye.classfile.ClassHierarchyResolver;
import io.smallrye.classfile.ClassHierarchyResolver.ClassHierarchyInfo;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassHierarchyResolverAnonymous2Test {
    private static final ClassDesc GENERATED_CLASS = ClassDesc.of("example", "GeneratedChild");
    private static final ClassDesc SUPER_CLASS = ClassDesc.of("example", "GeneratedParent");
    private static final String GENERATED_CLASS_RESOURCE = "example/GeneratedChild.class";

    @Test
    void parsesHierarchyInformationFromClassLoaderResource() {
        byte[] classBytes = ClassFile.of().build(
                GENERATED_CLASS,
                classBuilder -> classBuilder.withSuperclass(SUPER_CLASS));
        InMemoryClassResourceLoader loader = new InMemoryClassResourceLoader(GENERATED_CLASS_RESOURCE, classBytes);
        ClassHierarchyResolver resolver = ClassHierarchyResolver.ofResourceParsing(loader);

        ClassHierarchyInfo classInfo = resolver.getClassInfo(GENERATED_CLASS);

        assertThat(classInfo).isEqualTo(ClassHierarchyInfo.ofClass(SUPER_CLASS));
        assertThat(loader.resourceRequests).isEqualTo(1);
        assertThat(loader.lastRequestedResource).isEqualTo(GENERATED_CLASS_RESOURCE);
    }

    private static final class InMemoryClassResourceLoader extends ClassLoader {
        private final String resourceName;
        private final byte[] classBytes;
        private int resourceRequests;
        private String lastRequestedResource;

        private InMemoryClassResourceLoader(String resourceName, byte[] classBytes) {
            super(ClassHierarchyResolverAnonymous2Test.class.getClassLoader());
            this.resourceName = resourceName;
            this.classBytes = classBytes.clone();
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            resourceRequests++;
            lastRequestedResource = name;
            if (resourceName.equals(name)) {
                return new ByteArrayInputStream(classBytes);
            }
            return super.getResourceAsStream(name);
        }
    }
}
