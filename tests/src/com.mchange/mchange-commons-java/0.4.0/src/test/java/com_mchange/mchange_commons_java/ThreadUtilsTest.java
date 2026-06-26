/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.mchange_commons_java;

import com.mchange.v2.lang.ThreadUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ThreadUtilsTest {
    @Test
    void reflectiveHoldsLockReportsWhetherCurrentThreadOwnsMonitor() {
        Object lock = new Object();

        synchronized (lock) {
            assertThat(ThreadUtils.reflectiveHoldsLock(lock)).isTrue();
        }

        assertThat(ThreadUtils.reflectiveHoldsLock(lock)).isFalse();
    }
}
