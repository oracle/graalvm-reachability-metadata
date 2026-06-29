/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_sonatype_sisu.sisu_guice;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.internal.asm.ClassWriter;
import com.google.inject.internal.asm.Opcodes;
import com.google.inject.internal.asm.Type;
import com.google.inject.internal.cglib.core.ClassEmitter;
import com.google.inject.internal.cglib.core.CodeEmitter;
import com.google.inject.internal.cglib.core.EmitUtils;
import com.google.inject.internal.cglib.core.Signature;
import org.junit.jupiter.api.Test;

public class EmitUtilsTest {
    @Test
    void emitsClassArrayConstantsForAsmTypeValues() {
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        ClassEmitter classEmitter = new ClassEmitter(classWriter);
        classEmitter.begin_class(
                Opcodes.V1_5,
                Opcodes.ACC_PUBLIC,
                "org_sonatype_sisu.sisu_guice.EmitUtilsGeneratedConstants",
                Type.getType(Object.class),
                null,
                "EmitUtilsGeneratedConstants.java");
        Signature signature = new Signature("constantTypes", "()[Ljava/lang/Class;");
        CodeEmitter method = classEmitter.begin_method(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, signature, null);

        EmitUtils.push_array(method, new Type[] {Type.getType(String.class), Type.INT_TYPE});
        method.return_value();
        method.end_method();
        classEmitter.end_class();

        assertThat(classWriter.toByteArray()).isNotEmpty();
    }
}
