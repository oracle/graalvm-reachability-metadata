/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_database_jdbc.ojdbc11;

import static org.assertj.core.api.Assertions.assertThat;

import oracle.net.ns.NetErrorMessage;
import org.junit.jupiter.api.Test;

public class NetErrorMessageTest {
    @Test
    void resolvesOracleNetErrorText() {
        String message = new NetErrorMessage().getMessage(12514, null);

        assertThat(message).contains("12514").isNotBlank();
    }
}
