/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_protobuf.protobuf_java_util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.protobuf.Any;
import com.google.protobuf.Timestamp;
import com.google.protobuf.util.JsonFormat;
import com.google.protobuf.util.Timestamps;
import org.junit.jupiter.api.Test;

/**
 * JSON type registration and Any envelope handling are the API gateway subsystem exercised here.
 *
 * A gateway accepts an event envelope from an external client, resolves the payload type from its
 * registry, and forwards the typed envelope as canonical protobuf JSON.
 */
public class JsonFormatAnyEnvelopeTest {

    private static final String EVENT_JSON = """
            {
              "@type": "type.googleapis.com/google.protobuf.Timestamp",
              "value": "2024-04-18T10:47:03.875Z"
            }
            """;

    @Test
    void parsesAndForwardsRegisteredAnyPayload() throws Exception {
        JsonFormat.TypeRegistry registry =
                JsonFormat.TypeRegistry.newBuilder().add(Timestamp.getDescriptor()).build();
        Any.Builder envelopeBuilder = Any.newBuilder();

        JsonFormat.parser().usingTypeRegistry(registry).merge(EVENT_JSON, envelopeBuilder);
        Any envelope = envelopeBuilder.build();
        Timestamp receivedEvent = envelope.unpack(Timestamp.class);

        assertEquals(
                Timestamps.parse("2024-04-18T10:47:03.875Z"),
                receivedEvent);

        String forwardedJson =
                JsonFormat.printer()
                        .usingTypeRegistry(registry)
                        .omittingInsignificantWhitespace()
                        .print(envelope);

        assertEquals(
                "{\"@type\":\"type.googleapis.com/google.protobuf.Timestamp\","
                        + "\"value\":\"2024-04-18T10:47:03.875Z\"}",
                forwardedJson);
    }
}
