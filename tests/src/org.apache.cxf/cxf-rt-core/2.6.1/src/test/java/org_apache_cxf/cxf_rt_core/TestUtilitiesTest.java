/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_cxf.cxf_rt_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;

import org.apache.cxf.test.TestUtilities;
import org.junit.jupiter.api.Test;

public class TestUtilitiesTest {
    @Test
    void opensResourcesRelativeToTheConfiguredClasspathAnchor() throws Exception {
        TestUtilities utilities = new TestUtilities(TestUtilitiesTest.class);

        try (InputStream stream = utilities.getResourceAsStream("TestUtilitiesTest.class")) {
            assertThat(stream).isNotNull();
            assertThat(stream.readNBytes(4))
                    .containsExactly((byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE);
        }
    }
}
