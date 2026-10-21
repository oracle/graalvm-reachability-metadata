/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_tngtech_archunit.archunit;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.thirdparty.com.google.common.io.Files;
import java.io.File;
import org.junit.jupiter.api.Test;

public class TempFileCreatorInnerJavaNioCreatorTest {
    @Test
    void createsAnOwnerOnlyTemporaryDirectory() {
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
