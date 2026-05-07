/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_jnr.jnr_a64asm;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import jnr.a64asm.Assembler_A64;
import jnr.a64asm.CPU_A64;
import jnr.a64asm.Immediate;
import jnr.a64asm.Offset;
import jnr.a64asm.Post_index;
import jnr.a64asm.Pre_index;
import jnr.a64asm.Register;
import jnr.a64asm.Shift;

import org.junit.jupiter.api.Test;

import static jnr.a64asm.SHIFT_ENUM.LSL;
import static org.assertj.core.api.Assertions.assertThat;

public class Jnr_a64asmTest {
    private static final int NOP = 0xD503201F;
    private static final int MOVZ_X0_42 = 0xD2800540;
    private static final int ADD_X0_X0_8 = 0x91002000;
    private static final int RET_X0 = 0xD65F0000;
    private static final int STR_X0_X4_16 = 0xF9000880;
    private static final int LDR_X1_X4_16 = 0xF9400881;
    private static final int STR_X2_X4_NEGATIVE_16_PRE = 0xF81F0C82;
    private static final int LDR_X3_X4_16_POST = 0xF8410483;

    @Test
    void assemblesInstructionsIntoMachineCode() {
        Assembler_A64 assembler = new Assembler_A64(CPU_A64.A64);
        Shift noShift = new Shift(LSL, 0);
        Register x0 = Register.gpr(0);

        assembler.nop();
        assembler.movz(x0, Immediate.imm(42), noShift);
        assembler.add(x0, x0, Immediate.imm(8), noShift);
        assembler.ret(x0);

        int[] expectedInstructions = {NOP, MOVZ_X0_42, ADD_X0_X0_8, RET_X0};
        assertThat(assembler.offset()).isEqualTo(Integer.BYTES * expectedInstructions.length);
        assertThat(assembler.codeSize()).isEqualTo(Integer.BYTES * expectedInstructions.length);
        assertThat(dwordsFrom(assembler)).containsExactly(expectedInstructions);

        ByteBuffer relocatedCode = ByteBuffer.allocate(assembler.codeSize()).order(ByteOrder.LITTLE_ENDIAN);
        assembler.relocCode(relocatedCode, 0L);

        assertThat(relocatedCode.position()).isEqualTo(assembler.codeSize());
        for (int index = 0; index < expectedInstructions.length; index++) {
            assertThat(relocatedCode.getInt(index * Integer.BYTES)).isEqualTo(expectedInstructions[index]);
        }
    }

    @Test
    void assemblesLoadStoreAddressingModes() {
        Assembler_A64 assembler = new Assembler_A64(CPU_A64.A64);
        Register x0 = Register.gpr(0);
        Register x1 = Register.gpr(1);
        Register x2 = Register.gpr(2);
        Register x3 = Register.gpr(3);
        Register x4 = Register.gpr(4);

        assembler.str(x0, new Offset(x4, Immediate.imm(16)));
        assembler.ldr(x1, new Offset(x4, Immediate.imm(16)));
        assembler.str(x2, new Pre_index(x4, Immediate.imm(-16)));
        assembler.ldr(x3, new Post_index(x4, Immediate.imm(16)));

        int[] expectedInstructions = {
                STR_X0_X4_16,
                LDR_X1_X4_16,
                STR_X2_X4_NEGATIVE_16_PRE,
                LDR_X3_X4_16_POST
        };
        assertThat(dwordsFrom(assembler)).containsExactly(expectedInstructions);
    }

    private static int[] dwordsFrom(Assembler_A64 assembler) {
        int instructionCount = assembler.codeSize() / Integer.BYTES;
        int[] instructions = new int[instructionCount];
        for (int index = 0; index < instructionCount; index++) {
            instructions[index] = assembler.getDWordAt(index * Integer.BYTES);
        }
        return instructions;
    }
}
