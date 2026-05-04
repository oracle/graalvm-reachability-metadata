/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_jnr.jnr_x86asm;

import java.nio.ByteBuffer;

import jnr.x86asm.Assembler;
import jnr.x86asm.CONDITION;
import jnr.x86asm.ERROR_CODE;
import jnr.x86asm.HINT;
import jnr.x86asm.Immediate;
import jnr.x86asm.Label;
import jnr.x86asm.MMRegister;
import jnr.x86asm.Mem;
import jnr.x86asm.REG;
import jnr.x86asm.Register;
import jnr.x86asm.SEGMENT;
import jnr.x86asm.SIZE;
import jnr.x86asm.X87Register;
import jnr.x86asm.XMMRegister;

import org.junit.jupiter.api.Test;

import static jnr.x86asm.Asm.X86_32;
import static jnr.x86asm.Asm.X86_64;
import static jnr.x86asm.Asm.al;
import static jnr.x86asm.Asm.byte_ptr;
import static jnr.x86asm.Asm.dword_ptr;
import static jnr.x86asm.Asm.eax;
import static jnr.x86asm.Asm.ebx;
import static jnr.x86asm.Asm.ecx;
import static jnr.x86asm.Asm.edx;
import static jnr.x86asm.Asm.imm;
import static jnr.x86asm.Asm.ptr_abs;
import static jnr.x86asm.Asm.qword_ptr;
import static jnr.x86asm.Asm.rax;
import static jnr.x86asm.Asm.rcx;
import static jnr.x86asm.Asm.rdx;
import static jnr.x86asm.Asm.rsp;
import static jnr.x86asm.Asm.xmm0;
import static jnr.x86asm.Asm.xmm1;
import static jnr.x86asm.Asm.xmm2;
import static jnr.x86asm.Asm.xmmword_ptr;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

public class Jnr_x86asmTest {
    @Test
    void emitsSimpleMachineCodeAndCopiesItToByteBuffer() {
        Assembler assembler = new Assembler(X86_64);

        assembler.nop();
        assembler.int3();
        assembler.ud2();
        assembler.ret();

        assertThat(assembler.offset()).isEqualTo(5);
        assertThat(assembler.codeSize()).isEqualTo(5);
        assertThat(unsignedBytes(assembler)).containsExactly(0x90, 0xCC, 0x0F, 0x0B, 0xC3);

        ByteBuffer relocated = ByteBuffer.allocate(assembler.codeSize());
        assembler.relocCode(relocated, 0x1000L);
        assertThat(relocated.position()).isEqualTo(assembler.codeSize());
        assertThat(unsignedBytes(relocated)).containsExactly(0x90, 0xCC, 0x0F, 0x0B, 0xC3);
    }

    @Test
    void emitsRegisterImmediateAndMemoryAddressingForms() {
        Assembler assembler = new Assembler(X86_64);

        assembler.mov(eax, imm(0x12345678L));
        assembler.mov(qword_ptr(rax, 8), rdx);
        assembler.lea(rcx, qword_ptr(rax, rdx, 2, 16));
        assembler.ret();

        assertThat(unsignedBytes(assembler)).containsExactly(
                0xB8, 0x78, 0x56, 0x34, 0x12,
                0x48, 0x89, 0x50, 0x08,
                0x48, 0x8D, 0x4C, 0x90, 0x10,
                0xC3);
    }

    @Test
    void emitsThirtyTwoBitStackAndArithmeticInstructions() {
        Assembler assembler = new Assembler(X86_32);

        assembler.push(imm(0x7FL));
        assembler.pop(eax);
        assembler.add(eax, ebx);
        assembler.sub(eax, imm(1L));
        assembler.ret();

        assertThat(unsignedBytes(assembler)).containsExactly(
                0x6A, 0x7F,
                0x58,
                0x03, 0xC3,
                0x2D, 0x01, 0x00, 0x00, 0x00,
                0xC3);
    }

    @Test
    void emitsPackedSseVectorInstructions() {
        Assembler assembler = new Assembler(X86_64);

        assembler.movaps(xmm1, xmmword_ptr(rax, 32L));
        assembler.addps(xmm1, xmm0);
        assembler.pxor(xmm0, xmm0);
        assembler.movaps(xmmword_ptr(rax, 32L), xmm1);
        assembler.ret();

        assertThat(unsignedBytes(assembler)).containsExactly(
                0x0F, 0x28, 0x48, 0x20,
                0x0F, 0x58, 0xC8,
                0x66, 0x0F, 0xEF, 0xC0,
                0x0F, 0x29, 0x48, 0x20,
                0xC3);
    }

    @Test
    void supportsAlignmentAndInPlaceLittleEndianPatching() {
        Assembler assembler = new Assembler(X86_64);
        for (int i = 0; i < 8; i++) {
            assembler.nop();
        }

        assembler.setByteAt(0, (byte) 0xCC);
        assembler.setWordAt(1, (short) 0x1234);
        assembler.setDWordAt(3, 0x55667788);
        assembler.setQWordAt(0, 0x0102030405060708L);

        assertThat(assembler.getByteAt(0)).isEqualTo((byte) 0x08);
        assertThat(assembler.getWordAt(0)).isEqualTo((short) 0x0708);
        assertThat(assembler.getDWordAt(0)).isEqualTo(0x05060708);
        assertThat(assembler.getQWordAt(0)).isEqualTo(0x0102030405060708L);
        assertThat(unsignedBytes(assembler)).containsExactly(0x08, 0x07, 0x06, 0x05, 0x04, 0x03, 0x02, 0x01);

        assembler.align(16);
        assertThat(assembler.codeSize()).isEqualTo(16);
        assertThat(assembler.offset()).isEqualTo(16);
    }

    @Test
    void exposesRegisterFactoriesAndOperandPredicates() {
        assertThat(Register.gpd(0)).isSameAs(eax);
        assertThat(Register.gpq(0)).isSameAs(rax);
        assertThat(Register.gpr(REG.REG_RAX)).isSameAs(rax);
        assertThat(XMMRegister.xmm(2)).isSameAs(xmm2);
        assertThat(MMRegister.mm(3).isRegType(REG.REG_MM)).isTrue();
        assertThat(X87Register.st(1).isRegType(REG.REG_X87)).isTrue();

        assertThat(rax.isReg()).isTrue();
        assertThat(rax.isRegMem()).isTrue();
        assertThat(rax.size()).isEqualTo(SIZE.SIZE_QWORD);
        assertThat(rax.type()).isEqualTo(REG.REG_GPQ);
        assertThat(rax.index()).isZero();
        assertThat(rax.isRegCode(REG.REG_RAX)).isTrue();
        assertThat(rax.isRegIndex(0)).isTrue();
        assertThat(rax.isRegType(REG.REG_GPQ)).isTrue();
        assertThat(rax.isMem()).isFalse();

        assertThatIllegalArgumentException().isThrownBy(() -> Register.gpr(0x70));
    }

    @Test
    void exposesImmediateValuesSignednessAndOperandPredicates() {
        Immediate signed = Immediate.imm(-1L);
        Immediate unsigned = Immediate.uimm(0xFFFF_FFFFL);

        assertThat(signed).isSameAs(imm(-1L));
        assertThat(signed.isImm()).isTrue();
        assertThat(signed.isUnsigned()).isFalse();
        assertThat(signed.value()).isEqualTo(-1L);
        assertThat(signed.byteValue()).isEqualTo((byte) -1);
        assertThat(signed.shortValue()).isEqualTo((short) -1);
        assertThat(signed.intValue()).isEqualTo(-1);
        assertThat(signed.longValue()).isEqualTo(-1L);

        assertThat(unsigned.isImm()).isTrue();
        assertThat(unsigned.isUnsigned()).isTrue();
        assertThat(unsigned.longValue()).isEqualTo(0xFFFF_FFFFL);
    }

    @Test
    void createsMemoryOperandsForBaseIndexLabelAndAbsoluteAddresses() {
        Mem baseIndex = qword_ptr(rax, rdx, 2, -16L);
        assertThat(baseIndex.isMem()).isTrue();
        assertThat(baseIndex.isRegMem()).isTrue();
        assertThat(baseIndex.size()).isEqualTo(SIZE.SIZE_QWORD);
        assertThat(baseIndex.hasBase()).isTrue();
        assertThat(baseIndex.hasLabel()).isFalse();
        assertThat(baseIndex.base()).isEqualTo(rax.index());
        assertThat(baseIndex.index()).isEqualTo(rdx.index());
        assertThat(baseIndex.shift()).isEqualTo(2);
        assertThat(baseIndex.displacement()).isEqualTo(-16L);
        assertThat(baseIndex.segmentPrefix()).isEqualTo(SEGMENT.SEGMENT_NONE);

        Label label = new Label(7);
        Mem labelled = dword_ptr(label, rcx, 3, 24L);
        assertThat(labelled.isMem()).isTrue();
        assertThat(labelled.size()).isEqualTo(SIZE.SIZE_DWORD);
        assertThat(labelled.hasLabel()).isTrue();
        assertThat(labelled.label()).isSameAs(label);
        assertThat(labelled.index()).isEqualTo(rcx.index());
        assertThat(labelled.shift()).isEqualTo(3);
        assertThat(labelled.displacement()).isEqualTo(24L);

        Mem absolute = ptr_abs(0x1234L, 8L, SEGMENT.SEGMENT_FS);
        assertThat(absolute.isMem()).isTrue();
        assertThat(absolute.hasBase()).isFalse();
        assertThat(absolute.hasLabel()).isFalse();
        assertThat(absolute.target()).isEqualTo(0x1234L);
        assertThat(absolute.displacement()).isEqualTo(8L);
        assertThat(absolute.segmentPrefix()).isEqualTo(SEGMENT.SEGMENT_FS);
    }

    @Test
    void emitsConditionCodeMoveAndSetInstructions() {
        Assembler assembler = new Assembler(X86_64);

        assembler.cmp(eax, ebx);
        assembler.set(CONDITION.C_NOT_EQUAL, al);
        assembler.cmov(CONDITION.C_E, ecx, edx);
        assembler.setg(byte_ptr(rax, 1L));
        assembler.cmovl(ecx, dword_ptr(rax, 4L));
        assembler.ret();

        assertThat(unsignedBytes(assembler)).containsExactly(
                0x3B, 0xC3,
                0x0F, 0x95, 0xC0,
                0x0F, 0x44, 0xCA,
                0x0F, 0x9F, 0x40, 0x01,
                0x0F, 0x4C, 0x48, 0x04,
                0xC3);
    }

    @Test
    void exposesConditionHintSegmentAndErrorEnumerations() {
        assertThat(CONDITION.C_E.value()).isEqualTo(CONDITION.C_Z.value());
        assertThat(CONDITION.C_NOT_EQUAL.value()).isEqualTo(CONDITION.C_NE.value());
        assertThat(CONDITION.valueOf("C_GREATER")).isEqualTo(CONDITION.C_GREATER);

        assertThat(HINT.valueOf(HINT.HINT_TAKEN.value())).isEqualTo(HINT.HINT_TAKEN);
        assertThat(HINT.valueOf(HINT.HINT_NOT_TAKEN.value())).isEqualTo(HINT.HINT_NOT_TAKEN);
        assertThat(HINT.valueOf(999)).isEqualTo(HINT.HINT_NONE);

        assertThat(SEGMENT.SEGMENT_FS.prefix()).isEqualTo(0x64);
        assertThat(SEGMENT.SEGMENT_GS.prefix()).isEqualTo(SEGMENT.SEGMENT_FS.prefix());
        assertThat(ERROR_CODE.ERROR_NONE.intValue()).isZero();
        assertThat(ERROR_CODE.ERROR_ILLEGAL_INSTRUCTION.intValue()).isPositive();
    }

    @Test
    void handlesInstructionsThatRequireExtendedRegisters() {
        Assembler assembler = new Assembler(X86_64);

        assembler.mov(Register.gpq(8), imm(1L));
        assembler.add(Register.gpq(8), rax);
        assembler.mov(rsp, Register.gpq(8));
        assembler.ret();

        int[] bytes = unsignedBytes(assembler);
        assertThat(bytes).isNotEmpty();
        assertThat(bytes[0] & 0x40).isEqualTo(0x40);
        assertThat(bytes[bytes.length - 1]).isEqualTo(0xC3);

        ByteBuffer relocated = ByteBuffer.allocate(assembler.codeSize());
        assembler.relocCode(relocated, 0L);
        assertThat(unsignedBytes(relocated)).containsExactly(bytes);
    }

    private static int[] unsignedBytes(Assembler assembler) {
        int[] result = new int[assembler.codeSize()];
        for (int i = 0; i < result.length; i++) {
            result[i] = Byte.toUnsignedInt(assembler.getByteAt(i));
        }
        return result;
    }

    private static int[] unsignedBytes(ByteBuffer buffer) {
        ByteBuffer duplicate = buffer.duplicate();
        duplicate.flip();
        int[] result = new int[duplicate.remaining()];
        for (int i = 0; i < result.length; i++) {
            result[i] = Byte.toUnsignedInt(duplicate.get());
        }
        return result;
    }
}
