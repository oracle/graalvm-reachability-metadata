/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jna.jna;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JnaTest {
    @Test
    void test() {
        int number = CLibrary.INSTANCE.atol("42");
        assertThat(number).isEqualTo(42);
    }
}
