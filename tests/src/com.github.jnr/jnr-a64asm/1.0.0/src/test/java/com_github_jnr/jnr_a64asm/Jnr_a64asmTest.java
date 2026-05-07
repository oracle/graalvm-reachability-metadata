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

    private static int[] dwordsFrom(Assembler_A64 assembler) {
        int instructionCount = assembler.codeSize() / Integer.BYTES;
        int[] instructions = new int[instructionCount];
        for (int index = 0; index < instructionCount; index++) {
            instructions[index] = assembler.getDWordAt(index * Integer.BYTES);
        }
        return instructions;
    }
}
