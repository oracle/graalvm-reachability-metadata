/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_osgi.org_osgi_compendium;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.osgi.service.dmt.DmtData;

public class DmtData1Test {
    @Test
    void intConstructorCreatesIntegerDataAndExposesIntegerMetadata() {
        DmtData data = new DmtData(42);

        assertThat(data.getFormat()).isEqualTo(DmtData.FORMAT_INTEGER);
        assertThat(data.getInt()).isEqualTo(42);
        assertThat(data.getFormatName()).isEqualTo("integer");
        assertThat(data.getSize()).isEqualTo(Integer.BYTES);
    }
}
