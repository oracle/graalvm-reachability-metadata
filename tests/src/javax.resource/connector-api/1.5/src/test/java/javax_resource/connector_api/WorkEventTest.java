/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_resource.connector_api;

import java.io.IOException;

import javax.resource.spi.work.Work;
import javax.resource.spi.work.WorkCompletedException;
import javax.resource.spi.work.WorkEvent;
import javax.resource.spi.work.WorkException;
import javax.resource.spi.work.WorkManager;
import javax.resource.spi.work.WorkRejectedException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class WorkEventTest {
    @Test
    void identifiesAcceptedWorkWithoutAKnownStartDelay() {
        Object source = new Object();
        Work work = new MessageWork();
        WorkEvent event = new WorkEvent(source, WorkEvent.WORK_ACCEPTED, work, null);

        assertSame(source, event.getSource());
        assertSame(work, event.getWork());
        assertEquals(WorkEvent.WORK_ACCEPTED, event.getType());
        assertEquals(WorkManager.UNKNOWN, event.getStartDuration());
        assertNull(event.getException());
    }

    @Test
    void reportsTheDelayBeforeWorkStarted() {
        Work work = new MessageWork();
        WorkEvent event = new WorkEvent(this, WorkEvent.WORK_STARTED, work, null, 25L);

        assertEquals(WorkEvent.WORK_STARTED, event.getType());
        assertSame(work, event.getWork());
        assertEquals(25L, event.getStartDuration());
        assertNull(event.getException());
    }

    @Test
    void carriesTheReasonWorkWasRejected() {
        WorkRejectedException failure = new WorkRejectedException("Start deadline exceeded", WorkException.START_TIMED_OUT);
        WorkEvent event = new WorkEvent(this, WorkEvent.WORK_REJECTED, new MessageWork(), failure);

        assertEquals(WorkEvent.WORK_REJECTED, event.getType());
        assertSame(failure, event.getException());
        assertEquals(WorkException.START_TIMED_OUT, event.getException().getErrorCode());
        assertEquals("Start deadline exceeded, error code: " + WorkException.START_TIMED_OUT, event.getException().getMessage());
    }

    @Test
    void carriesTheCauseOfAWorkCompletionFailure() {
        IOException cause = new IOException("Remote system disconnected");
        WorkCompletedException failure = new WorkCompletedException("Delivery failed", cause);
        WorkEvent event = new WorkEvent(this, WorkEvent.WORK_COMPLETED, new MessageWork(), failure);

        assertEquals(WorkEvent.WORK_COMPLETED, event.getType());
        assertSame(failure, event.getException());
        assertSame(cause, event.getException().getCause());
        assertEquals("Delivery failed", event.getException().getMessage());
    }

    private static final class MessageWork implements Work {
        private final StringBuilder result = new StringBuilder();

        @Override
        public void run() {
            result.append("delivered");
        }

        @Override
        public void release() {
            result.setLength(0);
        }
    }
}
