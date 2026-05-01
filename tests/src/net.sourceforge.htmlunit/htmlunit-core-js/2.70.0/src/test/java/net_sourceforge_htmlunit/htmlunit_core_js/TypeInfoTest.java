/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sourceforge_htmlunit.htmlunit_core_js;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedList;

import net.sourceforge.htmlunit.corejs.classfile.ByteCode;
import net.sourceforge.htmlunit.corejs.classfile.ClassFileWriter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TypeInfoTest {
    private static final String GENERATED_CLASS_NAME =
            "net_sourceforge_htmlunit.htmlunit_core_js.GeneratedTypeInfoMerge";
    private static final Class<?> FIRST_MERGE_TYPE = ArrayList.class;
    private static final Class<?> SECOND_MERGE_TYPE = LinkedList.class;

    @Test
    void generatesStackMapForJoinOfDifferentObjectTypes() {
        ClassFileWriter classFileWriter =
                new ClassFileWriter(GENERATED_CLASS_NAME, "java.lang.Object", "TypeInfoTest.java");

        classFileWriter.startMethod(
                "chooseList", "(Z)Ljava/lang/Object;", ClassFileWriter.ACC_PUBLIC);
        int elseLabel = classFileWriter.acquireLabel();
        int joinLabel = classFileWriter.acquireLabel();

        classFileWriter.addILoad(1);
        classFileWriter.add(ByteCode.IFEQ, elseLabel);
        addDefaultConstructorCall(classFileWriter, getInternalName(FIRST_MERGE_TYPE));
        classFileWriter.add(ByteCode.GOTO, joinLabel);

        classFileWriter.markLabel(elseLabel, (short) 0);
        addDefaultConstructorCall(classFileWriter, getInternalName(SECOND_MERGE_TYPE));

        classFileWriter.markLabel(joinLabel);
        classFileWriter.add(ByteCode.ARETURN);
        classFileWriter.stopMethod((short) 2);

        byte[] classBytes = classFileWriter.toByteArray();

        assertThat(classBytes).startsWith((byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE);
        assertThat(new String(classBytes, StandardCharsets.ISO_8859_1)).contains("StackMapTable");
    }

    private static String getInternalName(Class<?> type) {
        return type.getName().replace('.', '/');
    }

    private static void addDefaultConstructorCall(
            ClassFileWriter classFileWriter, String className) {
        classFileWriter.add(ByteCode.NEW, className);
        classFileWriter.add(ByteCode.DUP);
        classFileWriter.addInvoke(ByteCode.INVOKESPECIAL, className, "<init>", "()V");
    }
}
