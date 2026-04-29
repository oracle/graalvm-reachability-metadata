/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjweaver;

import static aj.org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static aj.org.objectweb.asm.Opcodes.ACC_SUPER;
import static aj.org.objectweb.asm.Opcodes.ALOAD;
import static aj.org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static aj.org.objectweb.asm.Opcodes.RETURN;
import static aj.org.objectweb.asm.Opcodes.V1_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import aj.org.objectweb.asm.ClassWriter;
import aj.org.objectweb.asm.MethodVisitor;
import org.aspectj.apache.bcel.classfile.JavaClass;
import org.aspectj.apache.bcel.util.NonCachingClassLoaderRepository;
import org.junit.jupiter.api.Test;

public class NonCachingClassLoaderRepositoryTest {
    private static final String GENERATED_CLASS_NAME = "org_aspectj.aspectjweaver.generated.RepositoryLoadedFixture";
    private static final String GENERATED_CLASS_RESOURCE = GENERATED_CLASS_NAME.replace('.', '/') + ".class";

    @Test
    void loadClassReadsClassBytesFromClassLoaderResourceStream() throws Exception {
        byte[] classBytes = createClassBytes(GENERATED_CLASS_NAME.replace('.', '/'));
        ResourceBackedClassLoader classLoader = new ResourceBackedClassLoader(GENERATED_CLASS_RESOURCE, classBytes);
        NonCachingClassLoaderRepository repository = new NonCachingClassLoaderRepository(classLoader);

        JavaClass javaClass = repository.loadClass(GENERATED_CLASS_NAME);

        assertThat(javaClass.getClassName()).isEqualTo(GENERATED_CLASS_NAME);
        assertThat(classLoader.resourceRequests()).containsExactly(GENERATED_CLASS_RESOURCE);
    }

    private static byte[] createClassBytes(String internalClassName) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(V1_8, ACC_PUBLIC | ACC_SUPER, internalClassName, null, "java/lang/Object", null);
        writeDefaultConstructor(writer);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void writeDefaultConstructor(ClassWriter writer) {
        MethodVisitor constructor = writer.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(ALOAD, 0);
        constructor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        constructor.visitInsn(RETURN);
        constructor.visitMaxs(1, 1);
        constructor.visitEnd();
    }

    private static final class ResourceBackedClassLoader extends ClassLoader {
        private final String resourceName;
        private final byte[] classBytes;
        private final List<String> resourceRequests = new ArrayList<>();

        private ResourceBackedClassLoader(String resourceName, byte[] classBytes) {
            super(null);
            this.resourceName = resourceName;
            this.classBytes = classBytes;
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            resourceRequests.add(name);
            if (resourceName.equals(name)) {
                return new ByteArrayInputStream(classBytes);
            }
            return null;
        }

        private List<String> resourceRequests() {
            return resourceRequests;
        }
    }
}
