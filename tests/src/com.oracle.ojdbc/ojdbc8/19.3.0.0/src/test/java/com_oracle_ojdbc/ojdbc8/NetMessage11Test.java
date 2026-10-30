/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_ojdbc.ojdbc8;

import static org.assertj.core.api.Assertions.assertThat;

import oracle.net.ns.NetException;
import org.junit.jupiter.api.Test;

public class NetMessage11Test {
    @Test
    void explainsListenerRejectionWithTheServerErrorAndContext() {
        NetException error = new NetException(12514, "requested-service");

        assertThat(error.getMessage()).contains("ORA-12514", "requested-service")
                .doesNotContain("missing", "Undefined Error");
        assertThat(error.getErrorNumber()).isEqualTo(12514);
    }
}
