/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_logback.logback_classic;

import ch.qos.logback.classic.util.ClassicVersionUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassicVersionUtilTest {

    @Test
    void readsTheBundledVersionProperties() {
        assertThat(ClassicVersionUtil.getVersionBySelfDeclaredProperties()).isNotBlank();
    }
}
