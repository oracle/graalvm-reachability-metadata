/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_logmanager.jboss_logmanager;

import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/// Exercises per-logger attachments for request-scoped application state.
public class LoggerAttachmentTest {
    @Test
    @Timeout(60)
    public void preservesRequestStateAcrossLoggerUsers() {
        LogContext logContext = LogContext.create();
        Logger logger = logContext.getLogger("application.orders");
        Logger.AttachmentKey<RequestState> requestStateKey = new Logger.AttachmentKey<>();
        RequestState requestState = new RequestState("order-42", "reserved");

        assertNull(logger.getAttachment(requestStateKey));
        assertNull(logger.attachIfAbsent(requestStateKey, requestState));
        assertSame(requestState, logger.getAttachment(requestStateKey));
        assertEquals("order-42", logger.getAttachment(requestStateKey).orderId());
        assertEquals("reserved", logger.getAttachment(requestStateKey).status());

        RequestState replacement = new RequestState("order-43", "queued");
        assertSame(requestState, logger.attachIfAbsent(requestStateKey, replacement));
        assertSame(requestState, logger.detach(requestStateKey));
        assertNull(logger.getAttachment(requestStateKey));
    }

    private record RequestState(String orderId, String status) {
    }
}
