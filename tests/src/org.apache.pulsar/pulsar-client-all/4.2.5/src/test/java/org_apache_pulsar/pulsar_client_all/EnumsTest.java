/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.google.common.base.Enums;
import org.junit.jupiter.api.Test;

public class EnumsTest {
    @Test
    void exposesTheFieldThatDefinesAnEnumConstant() {
        assertThat(Enums.getField(State.READY).getName()).isEqualTo("READY");
        assertThat(Enums.getIfPresent(State.class, "STARTING").get()).isEqualTo(State.STARTING);
    }

    private enum State {
        STARTING,
        READY
    }
}
