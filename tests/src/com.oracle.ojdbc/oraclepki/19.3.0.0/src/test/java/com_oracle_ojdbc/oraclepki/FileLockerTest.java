/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_ojdbc.oraclepki;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import oracle.security.pki.FileLocker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class FileLockerTest {
    @TempDir Path directory;

    @Test
    void releasesLockSoAnotherLockerCanAcquireIt() throws Exception {
        Path file = Files.writeString(directory.resolve("wallet-data"), "content");
        FileLocker first = new FileLocker(file.toFile());
        try {
            assertThat(first.lock(false)).isTrue();
        } finally {
            first.unlock();
        }
        FileLocker second = new FileLocker(file.toFile());
        try {
            assertThat(second.lock(false)).isTrue();
            assertThat(Files.readString(file)).isEqualTo("content");
        } finally {
            second.unlock();
        }
    }
}
