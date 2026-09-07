/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.utility.MountableFile;

import static org.assertj.core.api.Assertions.assertThat;

public class MountableFileTest {
    @Test
    void resolvesRelativeAndAbsoluteClasspathResourceNames() throws Exception {
        MountableFile relative = MountableFile.forClasspathResource("testcontainers.properties");
        MountableFile absolute = MountableFile.forClasspathResource("/testcontainers.properties");

        assertThat(relative.getResolvedPath()).endsWith("testcontainers.properties");
        assertThat(absolute.getResolvedPath()).isEqualTo(relative.getResolvedPath());
    }
}
