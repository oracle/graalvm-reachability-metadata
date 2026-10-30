/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package asm.asm;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.CodeVisitor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassReaderTest {
    @Test
    void readsClassStructureFromTheSystemClassLoader() throws IOException {
        ClassReader reader = new ClassReader("org.objectweb.asm.ClassReader");
        List<String> classEvents = new ArrayList<>();
        List<String> fields = new ArrayList<>();
        List<String> methods = new ArrayList<>();

        reader.accept(new ClassVisitor() {
            @Override
            public void visit(int version, int access, String name, String superName,
                    String[] interfaces, String sourceFile) {
                classEvents.add(name + " extends " + superName);
            }

            @Override
            public void visitInnerClass(String name, String outerName, String innerName, int access) {
            }

            @Override
            public void visitField(int access, String name, String descriptor, Object value, Attribute attributes) {
                fields.add(name + descriptor);
            }

            @Override
            public CodeVisitor visitMethod(int access, String name, String descriptor,
                    String[] exceptions, Attribute attributes) {
                methods.add(name + descriptor);
                return null;
            }

            @Override
            public void visitAttribute(Attribute attribute) {
            }

            @Override
            public void visitEnd() {
                classEvents.add("end");
            }
        }, true);

        assertThat(classEvents).containsExactly("org/objectweb/asm/ClassReader extends java/lang/Object", "end");
        assertThat(fields).contains("b[B");
        assertThat(methods).contains("<init>(Ljava/lang/String;)V", "accept(Lorg/objectweb/asm/ClassVisitor;Z)V");
    }
}
