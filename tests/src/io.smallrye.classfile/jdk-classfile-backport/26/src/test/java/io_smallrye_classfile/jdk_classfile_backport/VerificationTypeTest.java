/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_smallrye_classfile.jdk_classfile_backport;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.util.List;

import io.smallrye.classfile.ClassFile;
import io.smallrye.classfile.TypeKind;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class VerificationTypeTest {
    @Test
    void verifyReportsOperandStackTypeMismatch() {
        ClassFile classFile = ClassFile.of(ClassFile.StackMapsOption.DROP_STACK_MAPS);
        byte[] classBytes = classFile.build(
                ClassDesc.of("io_smallrye_classfile.jdk_classfile_backport.VerificationTypeProbe"),
                classBuilder -> classBuilder.withMethodBody(
                        "wrongReturnType",
                        MethodTypeDesc.of(ConstantDescs.CD_int),
                        ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                        codeBuilder -> codeBuilder
                                .loadConstant(1L)
                                .return_(TypeKind.INT)));

        List<VerifyError> errors = classFile.verify(classBytes);

        List<String> messages = errors.stream()
                .map(VerifyError::getMessage)
                .toList();
        assertThat(messages).anySatisfy(message -> assertThat(message)
                .contains("Bad type on operand stack")
                .contains("integer_type is not assignable from long2_type"));
    }
}
