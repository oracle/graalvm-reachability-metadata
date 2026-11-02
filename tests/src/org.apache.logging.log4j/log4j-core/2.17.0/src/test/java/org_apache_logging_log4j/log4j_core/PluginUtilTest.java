/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_core;

import java.util.concurrent.BlockingQueue;

import org.apache.logging.log4j.core.async.ArrayBlockingQueueFactory;
import org.apache.logging.log4j.core.config.plugins.util.PluginUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PluginUtilTest {
    @Test
    void instantiatesAQueueFactoryAndUsesTheCreatedQueue() {
        ArrayBlockingQueueFactory<?> factory = PluginUtil.instantiatePlugin(ArrayBlockingQueueFactory.class);
        BlockingQueue<?> queue = factory.create(3);
        assertTrue(queue.isEmpty());
        assertEquals(3, queue.remainingCapacity());
    }
}
