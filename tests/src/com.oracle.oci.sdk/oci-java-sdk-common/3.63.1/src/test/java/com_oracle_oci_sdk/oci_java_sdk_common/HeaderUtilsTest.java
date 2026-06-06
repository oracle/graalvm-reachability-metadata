/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_oci_sdk.oci_java_sdk_common;

import static org.assertj.core.api.Assertions.assertThat;

import com.oracle.bmc.http.internal.HeaderUtils;
import org.junit.jupiter.api.Test;

public class HeaderUtilsTest {
    @Test
    void toValueCreatesEnumFromHeaderValue() {
        HeaderStatus status = HeaderUtils.toValue("opc-test-status", "available", HeaderStatus.class);

        assertThat(status).isEqualTo(HeaderStatus.AVAILABLE);
    }

    public enum HeaderStatus {
        AVAILABLE("available"),
        UNKNOWN_ENUM_VALUE(null);

        private final String value;

        HeaderStatus(String value) {
            this.value = value;
        }

        public static HeaderStatus create(String key) {
            for (HeaderStatus status : HeaderStatus.values()) {
                if (key.equals(status.value)) {
                    return status;
                }
            }
            return UNKNOWN_ENUM_VALUE;
        }
    }
}
