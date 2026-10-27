/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.core.Constants;

/** Verifies public constant discovery and value access. */
public class ConstantsTest {
    @Test
    void exposesPublicStaticFinalFields() {
        Constants constants = new Constants(ConstantFixture.class);

        assertThat(constants.getSize()).isEqualTo(2);
        assertThat(constants.asNumber("MAX_RETRIES")).isEqualTo(3);
        assertThat(constants.asString("MODE")).isEqualTo("safe");
    }

    public static final class ConstantFixture {
        public static final int MAX_RETRIES = 3;
        public static final String MODE = "safe";

        private ConstantFixture() { }
    }
}
