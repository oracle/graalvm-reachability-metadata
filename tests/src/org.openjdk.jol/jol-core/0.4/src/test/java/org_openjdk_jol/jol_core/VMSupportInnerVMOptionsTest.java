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
    void reportsVmDetailsFromDiscoveredVmOptions() {
        String details = VMSupport.vmDetails();

        assertThat(details)
                .contains("Running")
                .contains("Objects are")
                .contains("Field sizes by type")
                .contains("Array element sizes");
        assertThat(VMSupport.VM_NAME).isNotBlank();
        assertThat(VMSupport.OBJ_ALIGNMENT).isPositive();
        assertThat(VMSupport.REF_SIZE).isIn(4, 8);
        assertThat(VMSupport.BOOLEAN_SIZE).isEqualTo(1);
        assertThat(VMSupport.BYTE_SIZE).isEqualTo(1);
        assertThat(VMSupport.CHAR_SIZE).isEqualTo(Character.BYTES);
        assertThat(VMSupport.SHORT_SIZE).isEqualTo(Short.BYTES);
        assertThat(VMSupport.INT_SIZE).isEqualTo(Integer.BYTES);
        assertThat(VMSupport.FLOAT_SIZE).isEqualTo(Float.BYTES);
        assertThat(VMSupport.LONG_SIZE).isEqualTo(Long.BYTES);
        assertThat(VMSupport.DOUBLE_SIZE).isEqualTo(Double.BYTES);
    }
}
