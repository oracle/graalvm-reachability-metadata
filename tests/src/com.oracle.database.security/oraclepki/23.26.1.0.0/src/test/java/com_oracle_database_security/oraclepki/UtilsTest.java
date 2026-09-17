/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_database_security.oraclepki;

import static org.assertj.core.api.Assertions.assertThat;

import oracle.security.pki.util.Utils;
import org.junit.jupiter.api.Test;

public class UtilsTest {
    @Test
    void roundTripsUtf8AndHexEncodings() {
        byte[] utf8 = Utils.toUTF8("Oracle PKI");
        String hex = Utils.toHexString(utf8);

        assertThat(Utils.fromUTF8(utf8)).isEqualTo("Oracle PKI");
        assertThat(Utils.fromHexString(hex)).containsExactly(utf8);
    }
}
