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

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.ProtectionDomain;

import aj.org.objectweb.asm.ClassWriter;
import aj.org.objectweb.asm.MethodVisitor;
import org.aspectj.weaver.tools.cache.SimpleCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class SimpleCacheTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void initializesGeneratedClassWithoutProtectionDomain() {
        TestableSimpleCache cache = new TestableSimpleCache(temporaryDirectory);
        byte[] originalParentBytes = bytes("parent-before-weaving");
        byte[] wovenParentBytes = bytes("parent-after-weaving");
        String parentClassName = "org_aspectj.aspectjweaver.SimpleCacheParentWithoutProtectionDomain";
        String generatedClassName = "org_aspectj.aspectjweaver.generated.SimpleCacheGeneratedWithoutProtectionDomain";

        storeGeneratedClass(cache, parentClassName, originalParentBytes, wovenParentBytes, generatedClassName);

        byte[] initializedBytes = cache.getAndInitialize(
                parentClassName,
                originalParentBytes,
                new GeneratedClassLoader(),
                null);

        assertThat(initializedBytes).containsExactly(wovenParentBytes);
    }

    @Test
    void initializesGeneratedClassWithProtectionDomain() {
        TestableSimpleCache cache = new TestableSimpleCache(temporaryDirectory);
        byte[] originalParentBytes = bytes("protected-parent-before-weaving");
        byte[] wovenParentBytes = bytes("protected-parent-after-weaving");
        String parentClassName = "org_aspectj.aspectjweaver.SimpleCacheParentWithProtectionDomain";
        String generatedClassName = "org_aspectj.aspectjweaver.generated.SimpleCacheGeneratedWithProtectionDomain";
        ProtectionDomain protectionDomain = new ProtectionDomain(null, null);

        storeGeneratedClass(cache, parentClassName, originalParentBytes, wovenParentBytes, generatedClassName);

        byte[] initializedBytes = cache.getAndInitialize(
                parentClassName,
                originalParentBytes,
                new GeneratedClassLoader(),
                protectionDomain);

        assertThat(initializedBytes).containsExactly(wovenParentBytes);
    }

    private static void storeGeneratedClass(SimpleCache cache, String parentClassName, byte[] originalParentBytes,
            byte[] wovenParentBytes, String generatedClassName) {
        cache.put(parentClassName, originalParentBytes, wovenParentBytes);
        cache.put(generatedClassName, wovenParentBytes, generatedClassBytes(generatedClassName));
        cache.addGeneratedClassesNames(parentClassName, wovenParentBytes, generatedClassName);
    }

    private static byte[] generatedClassBytes(String className) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(V1_8, ACC_PUBLIC | ACC_SUPER, className.replace('.', '/'), null, "java/lang/Object", null);
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

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static final class TestableSimpleCache extends SimpleCache {
        private TestableSimpleCache(Path folder) {
            super(folder.toString(), true);
        }
    }

    private static final class GeneratedClassLoader extends ClassLoader {
    }
}
