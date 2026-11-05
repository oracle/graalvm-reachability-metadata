/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_protobuf.protobuf_java_util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.protobuf.BoolValue;
import com.google.protobuf.Duration;
import com.google.protobuf.util.JsonFormat;
import org.junit.jupiter.api.Test;

/**
 * JSON mapping of well-known duration and wrapper values is the policy configuration subsystem
 * exercised here.
 *
 * A retry service reads a duration and an enabled flag from JSON, then forwards their canonical
 * protobuf JSON representations to another service.
 */
public class JsonFormatWellKnownValueTest {

    @Test
    void parsesAndForwardsRetryPolicyValues() throws Exception {
        Duration.Builder timeoutBuilder = Duration.newBuilder();
        JsonFormat.parser().merge("\"2.500s\"", timeoutBuilder);
        Duration timeout = timeoutBuilder.build();

        BoolValue.Builder enabledBuilder = BoolValue.newBuilder();
        JsonFormat.parser().merge("true", enabledBuilder);
        BoolValue enabled = enabledBuilder.build();

        assertEquals(2L, timeout.getSeconds());
        assertEquals(500_000_000, timeout.getNanos());
        assertEquals(true, enabled.getValue());
        assertEquals("\"2.500s\"", JsonFormat.printer().print(timeout));
        assertEquals("true", JsonFormat.printer().print(enabled));
    }
}
