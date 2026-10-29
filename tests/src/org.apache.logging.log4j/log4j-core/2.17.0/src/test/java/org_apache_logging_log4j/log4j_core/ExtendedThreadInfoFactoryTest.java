/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_core;

import org.apache.logging.log4j.core.message.ExtendedThreadInfoFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class ExtendedThreadInfoFactoryTest {
    /// The factory accepts a runtime only when its `ThreadInfo` exposes lock information;
    /// otherwise its constructor throws `IllegalStateException`.
    ///
    /// The content of a thread dump is not asserted: Native Image does not implement
    /// `ThreadMXBean.dumpAllThreads` yet, see GR-38579 and
    /// https://github.com/oracle/graal/issues/4714.
    @Test
    void acceptsARuntimeWhoseThreadInfoExposesLockInformation() {
        assertDoesNotThrow(ExtendedThreadInfoFactory::new);
    }
}
