/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_oci_sdk.oci_java_sdk_common;

import com.oracle.bmc.http.internal.HeaderUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class HeaderUtilsTest {
    @Test
    void convertsHeaderValueUsingEnumFactoryMethod() {
        HeaderValue value = HeaderUtils.toValue("X-Header-Value", "enabled", HeaderValue.class);

        assertThat(value).isEqualTo(HeaderValue.ENABLED);
    }

    public enum HeaderValue {
        ENABLED,
        DISABLED;

        public static HeaderValue create(String value) {
            return HeaderValue.valueOf(value.toUpperCase());
        }
    }
}
