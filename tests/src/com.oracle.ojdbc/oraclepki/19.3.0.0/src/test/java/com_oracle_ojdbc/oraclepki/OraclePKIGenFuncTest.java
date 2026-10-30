/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_ojdbc.oraclepki;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import oracle.security.pki.textui.OraclePKIGenFunc;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class OraclePKIGenFuncTest {
    @TempDir Path directory;

    @Test
    void readsAndUpdatesCertificateSerialNumberFile() throws Exception {
        Path serialFile = Files.writeString(directory.resolve("serial.txt"), "12345\n");
        assertThat(OraclePKIGenFunc.getSerialNum(serialFile.toString())).isEqualTo(BigInteger.valueOf(12345));
        OraclePKIGenFunc.updateSerialNum(serialFile.toString(), BigInteger.valueOf(12346));
        assertThat(Files.readString(serialFile)).isEqualTo("12346");
        assertThat(OraclePKIGenFunc.getSerialNum(serialFile.toString())).isEqualTo(BigInteger.valueOf(12346));
    }
}
