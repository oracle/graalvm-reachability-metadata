/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_oci_sdk.oci_java_sdk_common;

import com.oracle.bmc.helper.EnvironmentVariablesHelper;
import java.util.Collections;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class EnvironmentVariablesHelperTest {
    @Test
    void setsEnvironmentToEmptyMap() throws Exception {
        EnvironmentVariablesHelper.setEnvironmentVariable(Collections.emptyMap());

        assertTrue(System.getenv().isEmpty());
    }
}
