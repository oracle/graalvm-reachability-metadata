/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_http_codec;

import org.junit.jupiter.api.Test;
import org.springframework.boot.http.codec.autoconfigure.HttpCodecsProperties;
import org.springframework.util.unit.DataSize;

import static org.assertj.core.api.Assertions.assertThat;

public class HttpCodecsPropertiesTest {
    @Test
    void defaultsLeaveCodecBufferLimitsUnchangedAndRequestLoggingDisabled() {
        HttpCodecsProperties properties = new HttpCodecsProperties();

        assertThat(properties.isLogRequestDetails()).isFalse();
        assertThat(properties.getMaxInMemorySize()).isNull();
    }

    @Test
    void requestLoggingAndBufferLimitsCanBeConfiguredAndReset() {
        HttpCodecsProperties properties = new HttpCodecsProperties();
        properties.setLogRequestDetails(true);
        properties.setMaxInMemorySize(DataSize.ofKilobytes(64));

        assertThat(properties.isLogRequestDetails()).isTrue();
        assertThat(properties.getMaxInMemorySize()).isEqualTo(DataSize.ofBytes(65536));

        properties.setLogRequestDetails(false);
        properties.setMaxInMemorySize(null);

        assertThat(properties.isLogRequestDetails()).isFalse();
        assertThat(properties.getMaxInMemorySize()).isNull();
    }
}
