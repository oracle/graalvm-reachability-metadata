/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_classfilewriter.jboss_classfilewriter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedList;

import org.jboss.classfilewriter.AccessFlag;
import org.jboss.classfilewriter.ClassFile;
import org.jboss.classfilewriter.ClassMethod;
import org.jboss.classfilewriter.code.BranchEnd;
import org.jboss.classfilewriter.code.CodeAttribute;
import org.junit.jupiter.api.Test;

class CodeAttributeTest {

    @Test
    void mergesDifferentObjectLocalsByLoadingBothTypesFromTheClassLoader() throws Exception {
        ClassLoader classLoader = CodeAttributeTest.class.getClassLoader();
        ClassFile classFile = new ClassFile(
                "org.jboss.classfilewriter.generated.CodeAttributeCoverage",
                Object.class.getName(),
                classLoader);
        ClassMethod method = classFile.addMethod(
                AccessFlag.of(AccessFlag.PUBLIC, AccessFlag.STATIC),
                "mergeLocalTypes",
                "Ljava/util/AbstractList;",
                "Z");
        CodeAttribute code = method.getCodeAttribute();

        code.iload(0);
        BranchEnd elseBranch = code.ifeq();

        code.newInstruction(ArrayList.class);
        code.dup();
        code.invokespecial(ArrayList.class.getDeclaredConstructor());
        code.astore(1);
        BranchEnd end = code.gotoInstruction();

        code.branchEnd(elseBranch);
        code.newInstruction(LinkedList.class);
        code.dup();
        code.invokespecial(LinkedList.class.getDeclaredConstructor());
        code.astore(1);

        code.branchEnd(end);
        code.aload(1);
        code.returnInstruction();

        assertThat(classFile.toBytecode()).isNotEmpty();
        assertThat(code.getStackFrames().values().stream()
                .anyMatch(frame -> frame.getLocalVariableState().getContents().size() > 1
                        && "Ljava/util/AbstractList;".equals(frame.getLocalVariableState().getContents().get(1).getDescriptor())))
                .isTrue();
    }
}
