/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.google.common.io.Files;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

public class TempFileCreatorInnerJavaNioCreatorTest {
    @Test
    void createsAnOwnerOnlyTemporaryDirectory() {
        File directory = Files.createTempDir();
        try {
            assertThat(directory).isDirectory().canWrite();
        } finally {
            assertThat(directory.delete()).isTrue();
        }
    }
}
