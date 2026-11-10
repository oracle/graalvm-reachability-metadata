/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.io.grpc.Status;
import com.couchbase.client.core.deps.io.grpc.StatusRuntimeException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class GrpcUtilAnonymous4Test {
    @Test
    void expiresUnansweredCallUsingChannelTimer() throws Exception {
        try (GrpcTestSupport service = new GrpcTestSupport(false)) {
            assertEquals("reply:ready", service.reply("ready").get(30, TimeUnit.SECONDS));
            ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> service.reply("wait").get(30, TimeUnit.SECONDS));
            StatusRuntimeException status = assertInstanceOf(StatusRuntimeException.class, failure.getCause());
            assertEquals(Status.Code.DEADLINE_EXCEEDED, status.getStatus().getCode());
        }
    }
}
