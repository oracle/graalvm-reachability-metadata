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
    void reportsVmDetailsWithDiscoveredPrimitiveFieldSizes() {
        String vmDetails = VMSupport.vmDetails();

        assertThat(vmDetails)
                .contains("Running ")
                .contains("Field sizes by type")
                .contains("Array element sizes");
        assertThat(VMSupport.BOOLEAN_SIZE).isPositive();
        assertThat(VMSupport.BYTE_SIZE).isPositive();
        assertThat(VMSupport.CHAR_SIZE).isPositive();
        assertThat(VMSupport.DOUBLE_SIZE).isPositive();
        assertThat(VMSupport.FLOAT_SIZE).isPositive();
        assertThat(VMSupport.INT_SIZE).isPositive();
        assertThat(VMSupport.LONG_SIZE).isPositive();
        assertThat(VMSupport.SHORT_SIZE).isPositive();
    }
}
