/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_oci_sdk.oci_java_sdk_common;

import static org.assertj.core.api.Assertions.assertThat;

import com.oracle.bmc.ClientRuntime;
import org.junit.jupiter.api.Test;

public class ClientRuntimeTest {
    @Test
    void getRuntimeLoadsSdkPropertiesFromClasspathResource() {
        ClientRuntime runtime = ClientRuntime.getRuntime();

        assertThat(runtime.getSdkVersion()).isNotBlank().isNotEqualTo("Unknown");
        assertThat(runtime.getClientInfo()).isEqualTo("Oracle-JavaSDK/" + runtime.getSdkVersion());
        assertThat(runtime.getUserAgent()).contains(runtime.getClientInfo());
        assertThat(runtime.getClientCompatibilityChecker()).isNotNull();
    }
}
