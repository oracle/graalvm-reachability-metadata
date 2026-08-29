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

/** Verifies reflective discovery of public constants. */
public class ConstantsTest {
    @Test
    void exposesPublicStaticFinalFields() {
        Constants constants = new Constants(FixtureConstants.class);

        assertThat(constants.getSize()).isEqualTo(2);
        assertThat(constants.asString("MODE_FAST")).isEqualTo("fast");
        assertThat(constants.asNumber("RETRY_COUNT")).isEqualTo(3);
    }

    public static final class FixtureConstants {
        public static final String MODE_FAST = "fast";
        public static final int RETRY_COUNT = 3;

        private FixtureConstants() { }
    }
}
