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

public class FileLockerTest {
    @TempDir Path directory;

    @Test
    void exclusivelyLocksAndUnlocksWalletFile() throws Exception {
        Path file = Files.writeString(directory.resolve("ewallet.p12"), "wallet");
        FileLocker locker = new FileLocker(file.toFile());

        assertThat(locker.lock(false)).isTrue();
        locker.unlock();
        assertThat(Files.readString(file)).isEqualTo("wallet");
    }
}
