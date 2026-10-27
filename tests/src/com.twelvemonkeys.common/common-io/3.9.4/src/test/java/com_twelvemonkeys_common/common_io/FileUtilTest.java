/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_twelvemonkeys_common.common_io;

import static org.assertj.core.api.Assertions.assertThat;

import com.twelvemonkeys.io.FileUtil;

import java.io.File;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class FileUtilTest {
    @Test
    void reportsFileSystemSpaceForExistingDirectory(@TempDir Path temporaryDirectory) {
        File directory = temporaryDirectory.toFile();

        long totalSpace = FileUtil.getTotalSpace(directory);
        long freeSpace = FileUtil.getFreeSpace(directory);
        long usableSpace = FileUtil.getUsableSpace(directory);

        assertThat(totalSpace).isEqualTo(directory.getTotalSpace()).isPositive();
        assertThat(freeSpace).isBetween(0L, totalSpace);
        assertThat(usableSpace).isBetween(0L, totalSpace);
    }
}
