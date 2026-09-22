/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_database_security.oraclepki;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import oracle.security.pki.FileLocker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class FileLockProviderTest {
    @TempDir Path directory;

    @Test
    void coordinatesSharedFileLocks() throws Exception {
        Path file = Files.writeString(directory.resolve("wallet.p12"), "wallet");
        FileLocker first = new FileLocker(file.toFile());
        FileLocker second = new FileLocker(file.toFile());

        assertThat(first.lock(true)).isTrue();
        assertThat(second.lock(true)).isTrue();
        second.unlock();
        first.unlock();
    }
}
