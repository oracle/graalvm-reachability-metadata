/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mozilla.rhino;

import org.junit.jupiter.api.Test;
import org.mozilla.classfile.ByteCode;
import org.mozilla.classfile.ClassFileWriter;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class TypeInfoTest {

    @Test
    void stackMapGenerationMergesDifferentReferenceTypesAtBranchJoin() {
        // The test resources include ClassFileWriter.class with a Java 6 classfile header so Rhino enables
        // StackMapTable generation and exercises TypeInfo reference merging.
        ClassFileWriter writer = new ClassFileWriter(
                "org.example.TypeInfoBranchJoinSample",
                "java.lang.Object",
                "TypeInfoBranchJoinSample.java");

        short methodFlags = ClassFileWriter.ACC_PUBLIC | ClassFileWriter.ACC_STATIC;
        writer.startMethod("choose", "(Z)Ljava/lang/Object;", methodFlags);
        int elseLabel = writer.acquireLabel();
        int joinLabel = writer.acquireLabel();

        writer.addILoad(0);
        writer.add(ByteCode.IFEQ, elseLabel);
        writer.addPush("string branch");
        writer.add(ByteCode.GOTO, joinLabel);

        writer.markLabel(elseLabel, (short) 0);
        writer.add(ByteCode.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");

        writer.markLabel(joinLabel);
        writer.add(ByteCode.ARETURN);
        writer.stopMethod((short) 1);

        byte[] classBytes = writer.toByteArray();
        String classFileText = new String(classBytes, StandardCharsets.ISO_8859_1);
        assertThat(classBytes).startsWith((byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE);
        assertThat(classFileText).contains("StackMapTable");
    }
}
