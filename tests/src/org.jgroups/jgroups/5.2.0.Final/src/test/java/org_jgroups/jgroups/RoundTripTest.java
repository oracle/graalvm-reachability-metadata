/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import org.jgroups.tests.RoundTrip;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

public class RoundTripTest {
    @Test
    void commandLineHelpCreatesSelectedTransport() {
        assertThatCode(() -> RoundTrip.main(new String[] {"-tp", "udp", "-h"}))
            .doesNotThrowAnyException();
    }
}
