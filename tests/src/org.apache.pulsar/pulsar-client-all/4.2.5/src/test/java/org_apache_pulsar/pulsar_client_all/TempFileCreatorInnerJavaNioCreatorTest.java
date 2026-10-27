/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import org.apache.pulsar.shade.com.google.common.io.Files;
import org.junit.jupiter.api.Test;

public class TempFileCreatorInnerJavaNioCreatorTest {
    @Test
    void createsAPrivateTemporaryDirectory() {
        File directory = Files.createTempDir();
        try {
            assertThat(directory).isDirectory();
            assertThat(directory.canRead()).isTrue();
            assertThat(directory.canWrite()).isTrue();
        } finally {
            assertThat(directory.delete()).isTrue();
        }
    }
}
