/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_azure.azure_storage_common;

import static org.assertj.core.api.Assertions.assertThat;

import com.azure.storage.common.sas.SasIpRange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(60)
public class SasIpRangeTest {
    @Test
    void parsesSingleAddressAndBoundedRange() {
        SasIpRange singleAddress = SasIpRange.parse("192.0.2.10");
        SasIpRange boundedRange = SasIpRange.parse("192.0.2.10-192.0.2.25");

        assertThat(singleAddress.getIpMin()).isEqualTo("192.0.2.10");
        assertThat(singleAddress.getIpMax()).isNull();
        assertThat(singleAddress.toString()).isEqualTo("192.0.2.10");
        assertThat(boundedRange.getIpMin()).isEqualTo("192.0.2.10");
        assertThat(boundedRange.getIpMax()).isEqualTo("192.0.2.25");
        assertThat(boundedRange.toString()).isEqualTo("192.0.2.10-192.0.2.25");
    }

    @Test
    void rendersAConsumerConfiguredRange() {
        SasIpRange range = new SasIpRange().setIpMin("198.51.100.4").setIpMax("198.51.100.18");

        assertThat(range.getIpMin()).isEqualTo("198.51.100.4");
        assertThat(range.getIpMax()).isEqualTo("198.51.100.18");
        assertThat(range.toString()).isEqualTo("198.51.100.4-198.51.100.18");
    }
}
