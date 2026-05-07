/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_jnr.jnr_a64asm;

import jnr.a64asm.INST_CODE;
import jnr.a64asm.InstructionDescription;
import jnr.a64asm.InstructionGroup;
import jnr.a64asm.PREF_ENUM;
import jnr.a64asm.PRFOP_ENUM;
import jnr.a64asm.SYSREG_CODE;
import jnr.a64asm.SysRegDescription;
import jnr.a64asm.SysRegister;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Jnr_a64asmInstructionMetadataTest {
    @Test
    void looksUpRepresentativeInstructionDescriptions() {
        INST_CODE[] instructionCodes = {
                INST_CODE.INST_ADD_ADDSUB_IMM,
                INST_CODE.INST_ADD_ADDSUB_SHIFT,
                INST_CODE.INST_B_BRANCH_IMM,
                INST_CODE.INST_BEQ_CONDBRANCH,
                INST_CODE.INST_BR_BRANCH_REG,
                INST_CODE.INST_LDR_IMM_OFF,
                INST_CODE.INST_STR_LDST_POS,
                INST_CODE.INST_MOVZ_MOVEWIDE,
                INST_CODE.INST_MSR_IC_SYSTEM,
                INST_CODE.INST_SVC_EXCEPTION
        };

        for (INST_CODE instructionCode : instructionCodes) {
            InstructionDescription description = InstructionDescription.find(instructionCode);

            assertThat(description).isNotNull();
            assertThat(InstructionDescription.find(instructionCode)).isSameAs(description);
        }
    }

    @Test
    void exposesInstructionGroupCatalog() {
        assertThat(InstructionGroup.valueOf("addsub_imm")).isSameAs(InstructionGroup.addsub_imm);
        assertThat(InstructionGroup.valueOf("branch_imm")).isSameAs(InstructionGroup.branch_imm);
        assertThat(InstructionGroup.valueOf("ic_system")).isSameAs(InstructionGroup.ic_system);
        assertThat(InstructionGroup.values()).contains(
                InstructionGroup.addsub_carry,
                InstructionGroup.addsub_ext,
                InstructionGroup.condbranch,
                InstructionGroup.exception,
                InstructionGroup.ldst_pos,
                InstructionGroup.movewide);
    }

    @Test
    void looksUpRepresentativeSystemRegisterDescriptions() {
        SYSREG_CODE[] registerCodes = {
                SYSREG_CODE.NZCV,
                SYSREG_CODE.FPCR,
                SYSREG_CODE.FPSR,
                SYSREG_CODE.SCTLR_EL1,
                SYSREG_CODE.TPIDR_EL0
        };

        for (SYSREG_CODE registerCode : registerCodes) {
            SysRegDescription description = SysRegDescription.find(registerCode);
            SysRegister register = SysRegister.sysReg(registerCode);

            assertThat(description).isNotNull();
            assertThat(SysRegDescription.find(registerCode)).isSameAs(description);
            assertThat(register.getEnum()).isSameAs(registerCode);
            assertThat(register.size()).isEqualTo(64);
        }
    }

    @Test
    void exposesPrefetchOperationCatalogs() {
        assertThat(PREF_ENUM.valueOf("PLDL1KEEP")).isSameAs(PREF_ENUM.PLDL1KEEP);
        assertThat(PREF_ENUM.values()).contains(
                PREF_ENUM.PLDL1KEEP,
                PREF_ENUM.PLDL2STRM,
                PREF_ENUM.PLIL1KEEP,
                PREF_ENUM.PSTL3STRM);

        PRFOP_ENUM dataKeep = new PRFOP_ENUM(PRFOP_ENUM.PLDL1KEEP);
        PRFOP_ENUM instructionStream = new PRFOP_ENUM(PRFOP_ENUM.PLIL2STRM);
        PRFOP_ENUM storeKeep = new PRFOP_ENUM(PRFOP_ENUM.PSTL1KEEP);

        assertThat(dataKeep.isPrfop()).isTrue();
        assertThat(dataKeep.intValue()).isEqualTo(PRFOP_ENUM.PLDL1KEEP);
        assertThat(instructionStream.isPrfop()).isTrue();
        assertThat(instructionStream.intValue()).isEqualTo(PRFOP_ENUM.PLIL2STRM);
        assertThat(storeKeep.isPrfop()).isTrue();
        assertThat(storeKeep.intValue()).isEqualTo(PRFOP_ENUM.PSTL1KEEP);
    }
}
