/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_classfilewriter.jboss_classfilewriter;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;

import org.jboss.classfilewriter.AccessFlag;
import org.jboss.classfilewriter.ClassFactory;
import org.jboss.classfilewriter.ClassFile;
import org.jboss.classfilewriter.ClassMethod;
import org.jboss.classfilewriter.code.BranchEnd;
import org.jboss.classfilewriter.code.CodeAttribute;
import org.junit.jupiter.api.Test;

public class CodeAttributeTest {
    private static final ClassFactory UNUSED_CLASS_FACTORY = (loader, name, bytecode, offset, length, protectionDomain) -> {
        throw new AssertionError("define() should not be called by this test");
    };

    @Test
    void mergesBranchStackFramesUsingLoadedConcreteTypesToResolveCommonSuperclass() throws IOException {
        String className = generatedClassName("GeneratedCollectionChooser");
        ClassFile classFile = new ClassFile(
                className,
                AccessFlag.of(AccessFlag.SUPER, AccessFlag.PUBLIC),
                Object.class.getName(),
                getClass().getClassLoader(),
                UNUSED_CLASS_FACTORY
        );

        ClassMethod chooseImplementationMethod = classFile.addMethod(
                AccessFlag.of(AccessFlag.PUBLIC, AccessFlag.STATIC),
                "chooseImplementation",
                "Ljava/lang/Object;",
                "Z"
        );
        CodeAttribute code = chooseImplementationMethod.getCodeAttribute();
        code.iload(0);
        BranchEnd useLinkedList = code.ifeq();

        code.newInstruction(ArrayList.class);
        code.dup();
        code.invokespecial(ArrayList.class.getName(), "<init>", "V", new String[0]);
        BranchEnd methodEnd = code.gotoInstruction();

        code.branchEnd(useLinkedList);
        code.newInstruction(LinkedList.class);
        code.dup();
        code.invokespecial(LinkedList.class.getName(), "<init>", "V", new String[0]);

        code.branchEnd(methodEnd);
        code.returnInstruction();

        byte[] bytecode = classFile.toBytecode();

        assertThat(bytecode).isNotEmpty();
        assertThat(containsUtf8Constant(bytecode, "java/util/AbstractList")).isTrue();
    }

    private static String generatedClassName(String simpleName) {
        return CodeAttributeTest.class.getPackageName() + "." + simpleName;
    }

    private static boolean containsUtf8Constant(byte[] bytecode, String constant) throws IOException {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytecode))) {
            assertThat(input.readInt()).isEqualTo(0xCAFEBABE);
            input.readUnsignedShort();
            input.readUnsignedShort();

            int constantPoolCount = input.readUnsignedShort();
            for (int index = 1; index < constantPoolCount;) {
                int tag = input.readUnsignedByte();
                switch (tag) {
                    case 1 -> {
                        if (constant.equals(input.readUTF())) {
                            return true;
                        }
                    }
                    case 3, 4 -> input.skipBytes(4);
                    case 5, 6 -> input.skipBytes(8);
                    case 7, 8, 16, 19, 20 -> input.skipBytes(2);
                    case 9, 10, 11, 12, 17, 18 -> input.skipBytes(4);
                    case 15 -> input.skipBytes(3);
                    default -> throw new IllegalArgumentException("Unsupported constant-pool tag: " + tag);
                }
                index += tag == 5 || tag == 6 ? 2 : 1;
            }
        }
        return false;
    }
}
