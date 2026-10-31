/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty_websocket.jetty_websocket_jetty_api;

import org.eclipse.jetty.websocket.api.StatusCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StatusCodeTest {
    @Test
    void distinguishesSendableCloseCodesFromReservedSentinels() {
        for (int code : new int[]{StatusCode.NORMAL, StatusCode.SHUTDOWN, StatusCode.PROTOCOL,
                StatusCode.BAD_DATA, StatusCode.BAD_PAYLOAD, StatusCode.POLICY_VIOLATION,
                StatusCode.MESSAGE_TOO_LARGE, StatusCode.REQUIRED_EXTENSION, StatusCode.SERVER_ERROR,
                StatusCode.SERVICE_RESTART, StatusCode.TRY_AGAIN_LATER, StatusCode.INVALID_UPSTREAM_RESPONSE}) {
            assertTrue(StatusCode.isTransmittable(code), "Sendable close code: " + code);
        }
        for (int code : new int[]{StatusCode.UNDEFINED, StatusCode.NO_CODE, StatusCode.NO_CLOSE,
                StatusCode.FAILED_TLS_HANDSHAKE, -1, 999}) {
            assertFalse(StatusCode.isTransmittable(code), "Reserved close code: " + code);
        }
    }
}
