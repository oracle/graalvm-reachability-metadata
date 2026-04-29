/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_openjdk_jol.jol_core;

import org.junit.jupiter.api.Test;
import org.openjdk.jol.util.VMSupport;

import static org.assertj.core.api.Assertions.assertThat;

public class VMSupportInnerVMOptionsTest {
    @Test
    void computesVmOptionSizes() {
        assertThat(VMSupport.VM_NAME).isNotBlank();
        assertThat(VMSupport.ADDRESS_SIZE).isPositive();
        assertThat(VMSupport.OBJ_ALIGNMENT).isPositive();
        assertThat(VMSupport.REF_SIZE).isPositive();
        assertThat(VMSupport.BOOLEAN_SIZE).isPositive();
        assertThat(VMSupport.BYTE_SIZE).isPositive();
        assertThat(VMSupport.CHAR_SIZE).isPositive();
        assertThat(VMSupport.DOUBLE_SIZE).isPositive();
        assertThat(VMSupport.FLOAT_SIZE).isPositive();
        assertThat(VMSupport.INT_SIZE).isPositive();
        assertThat(VMSupport.LONG_SIZE).isPositive();
        assertThat(VMSupport.SHORT_SIZE).isPositive();
    }

    @Test
    void reportsVmDetailsFromDetectedOptions() {
        String details = VMSupport.vmDetails();

        assertThat(details).contains("Running");
        assertThat(details).contains(VMSupport.VM_NAME);
        assertThat(details).contains("Objects are");
        assertThat(details).contains("Field sizes by type");
        assertThat(details).contains("Array element sizes");
    }
}
