/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.junit_jupiter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.lifecycle.Startable;

@Testcontainers
public class TestcontainersExtensionTest {
    @Container
    private static final RecordingStartable SHARED_CONTAINER = new RecordingStartable();

    @Test
    void startsContainerDeclaredOnAnnotatedField() {
        assertEquals(1, SHARED_CONTAINER.startCount());
        assertTrue(SHARED_CONTAINER.isRunning());
    }

    private static final class RecordingStartable implements Startable {
        private int startCount;
        private boolean running;

        @Override
        public void start() {
            startCount++;
            running = true;
        }

        @Override
        public void stop() {
            running = false;
        }

        int startCount() {
            return startCount;
        }

        boolean isRunning() {
            return running;
        }
    }
}
