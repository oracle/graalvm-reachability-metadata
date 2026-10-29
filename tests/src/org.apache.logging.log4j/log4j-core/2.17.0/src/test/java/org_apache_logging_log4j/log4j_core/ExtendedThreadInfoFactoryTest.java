/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_core;

import java.util.Map;

import org.apache.logging.log4j.core.message.ExtendedThreadInfoFactory;
import org.apache.logging.log4j.message.ThreadInformation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ExtendedThreadInfoFactoryTest {
    @Test
    void includesTheCallingThreadInAnExtendedThreadDump() {
        Map<ThreadInformation, StackTraceElement[]> threads = new ExtendedThreadInfoFactory().createThreadInfo();
        assertFalse(threads.isEmpty());
        StringBuilder dump = new StringBuilder();
        threads.forEach((information, stack) -> {
            information.printThreadInfo(dump);
            information.printStack(dump, stack);
        });
        assertTrue(dump.toString().contains(Thread.currentThread().getName()));
        assertTrue(dump.toString().contains("includesTheCallingThreadInAnExtendedThreadDump"));
    }
}
