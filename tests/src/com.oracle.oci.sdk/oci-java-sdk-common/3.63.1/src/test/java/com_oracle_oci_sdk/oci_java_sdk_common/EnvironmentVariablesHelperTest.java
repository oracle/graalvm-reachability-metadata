/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_oci_sdk.oci_java_sdk_common;

import static org.assertj.core.api.Assertions.assertThat;

import com.oracle.bmc.helper.EnvironmentVariablesHelper;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class EnvironmentVariablesHelperTest {
    @Test
    void setEnvironmentVariableUpdatesSystemEnvironment() throws Exception {
        Map<String, String> originalEnvironment = new HashMap<>(System.getenv());
        String variableName = "OCI_JAVA_SDK_COMMON_TEST_ENVIRONMENT_VARIABLE";
        String variableValue = "native-image-reachability-test";
        Map<String, String> updatedEnvironment = new HashMap<>(originalEnvironment);
        updatedEnvironment.put(variableName, variableValue);

        try {
            EnvironmentVariablesHelper.setEnvironmentVariable(updatedEnvironment);

            assertThat(System.getenv(variableName)).isEqualTo(variableValue);
        } finally {
            EnvironmentVariablesHelper.setEnvironmentVariable(originalEnvironment);
        }
    }
}
