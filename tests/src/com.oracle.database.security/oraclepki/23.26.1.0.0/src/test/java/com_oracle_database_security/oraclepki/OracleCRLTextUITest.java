/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_database_security.oraclepki;

import static org.assertj.core.api.Assertions.assertThat;

import oracle.security.pki.textui.OracleCRLTextUI;
import org.junit.jupiter.api.Test;

public class OracleCRLTextUITest {
    @Test
    void reportsUsageForHelpCommand() {
        assertThat(OracleCRLTextUI.command(new String[] {"help"})).isEqualTo(-1);
    }
}
