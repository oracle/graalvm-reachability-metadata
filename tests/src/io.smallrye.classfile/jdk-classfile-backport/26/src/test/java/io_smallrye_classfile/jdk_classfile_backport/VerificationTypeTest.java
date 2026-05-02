/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_smallrye_classfile.jdk_classfile_backport;

import static org.assertj.core.api.Assertions.assertThat;

import io.smallrye.classfile.ClassFile;
import io.smallrye.classfile.extras.constant.ExtraConstantDescs;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.util.List;
import org.junit.jupiter.api.Test;

public class VerificationTypeTest {
    private static final ClassDesc GENERATED_CLASS = ClassDesc.of("coverage.GeneratedVerificationTypeCoverage");
    private static final MethodTypeDesc STRING_RETURN = MethodTypeDesc.of(ConstantDescs.CD_String);

    @Test
    void verifyReportsReturnTypeMismatchWithVerificationTypeNames() {
        ClassFile classFile = ClassFile.of(ClassFile.StackMapsOption.DROP_STACK_MAPS);
        byte[] classBytes = classFile.build(GENERATED_CLASS,
                classBuilder -> classBuilder.withFlags(ClassFile.ACC_PUBLIC)
                        .withMethodBody(ExtraConstantDescs.INIT_NAME, ConstantDescs.MTD_void,
                                ClassFile.ACC_PUBLIC,
                                codeBuilder -> codeBuilder.aload(0)
                                        .invokespecial(ConstantDescs.CD_Object,
                                                ExtraConstantDescs.INIT_NAME,
                                                ConstantDescs.MTD_void)
                                        .return_())
                        .withMethodBody("returnsString", STRING_RETURN,
                                ClassFile.ACC_PUBLIC + ClassFile.ACC_STATIC,
                                codeBuilder -> codeBuilder.iconst_1()
                                        .ireturn()));

        List<VerifyError> errors = classFile.verify(classBytes);

        assertThat(errors).singleElement()
                .extracting(Throwable::getMessage)
                .asString()
                .contains("Bad return type")
                .contains("java/lang/String is not assignable from integer_type");
    }
}
