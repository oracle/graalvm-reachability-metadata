/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_sshd.sshd_common;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.sshd.common.util.threads.ThreadUtils;
import org.junit.jupiter.api.Test;

public class ThreadUtilsTest {
    @Test
    void resolvesClassesUsingDefaultClassLoaders() {
        assertThat(ThreadUtils.resolveDefaultClass(ThreadUtilsTest.class, String.class.getName()))
                .isEqualTo(String.class);
        assertThat(ThreadUtils.resolveDefaultClass(
                ThreadUtilsTest.class, "missing.sshd.ThreadFactory")).isNull();
    }
}
