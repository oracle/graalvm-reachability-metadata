/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_code_gson.gson;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.MalformedJsonException;
import java.io.EOFException;
import java.io.IOException;
import java.io.StringReader;
import java.io.Writer;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Parsing strictness and error reporting at the `Gson` facade: `GsonBuilder.setStrictness`,
 * trailing-data and truncation detection, empty input, and the exceptions an application maps to
 * client errors.
 *
 * A webhook gateway receives order events from partners. Old partners send hand-written,
 * JavaScript-flavoured JSON that the default (lenient) `Gson` accepts; new partners are held to
 * RFC 8259 by a `Gson` built with `Strictness.STRICT`. The gateway turns every parse failure
 * into a rejection reason that names the offending JSON path, and treats an empty body as "no
 * event" rather than as an error.
 */
public class PartnerWebhookIngestionStrictnessTest {

    private static final Gson LEGACY_PARTNERS = new Gson();

    private static final Gson STRICT_PARTNERS = new GsonBuilder()
            .setStrictness(Strictness.STRICT)
            .create();

    @Test
    void defaultGsonAcceptsJavaScriptFlavouredPayloadsFromLegacyPartners() {
        String payload = """
                // sent by the partner's PHP plugin
                {
                  orderId: 'ORD-1001',
                  'status': SHIPPED,
                  amountCents: 4599,
                  tags: ['gift'; 'express']
                }
                """;

        OrderEvent event = LEGACY_PARTNERS.fromJson(payload, OrderEvent.class);

        assertEquals("ORD-1001", event.orderId);
        assertEquals(Status.SHIPPED, event.status);
        assertEquals(4599, event.amountCents);
        assertEquals(List.of("gift", "express"), event.tags);
    }

    @Test
    void strictGsonRejectsTheSamePayloadAndReportsWhere() {
        String payload = """
                {"orderId": "ORD-1001", "status": 'SHIPPED', "amountCents": 4599}
                """;

        Rejection rejection = Gateway.ingest(STRICT_PARTNERS, payload);

        assertEquals(Rejection.Kind.MALFORMED, rejection.kind());
        assertTrue(rejection.detail().contains("$.status"), rejection.detail());
        Gson derived = STRICT_PARTNERS.newBuilder().setPrettyPrinting().create();
        assertEquals(Rejection.Kind.MALFORMED, Gateway.ingest(derived, payload).kind());
    }

    @Test
    void strictGsonAcceptsWellFormedPayloads() {
        String payload = """
                {"orderId": "ORD-1002", "status": "PAID", "amountCents": 120, "tags": []}
                """;

        OrderEvent event = STRICT_PARTNERS.fromJson(payload, OrderEvent.class);

        assertEquals("ORD-1002", event.orderId);
        assertEquals(Status.PAID, event.status);
        assertEquals(120, event.amountCents);
        assertEquals(List.of(), event.tags);
    }

    @Test
    void legacyStrictToleratesQuirksThatStrictRejects() {
        Gson legacyStrict = new GsonBuilder().setStrictness(Strictness.LEGACY_STRICT).create();
        String escapedApostrophe = "{\"orderId\": \"O\\'Brien-7\", \"status\": \"PAID\"}";

        OrderEvent event = legacyStrict.fromJson(escapedApostrophe, OrderEvent.class);
        assertEquals("O'Brien-7", event.orderId);

        Rejection rejection = Gateway.ingest(STRICT_PARTNERS, escapedApostrophe);
        assertEquals(Rejection.Kind.MALFORMED, rejection.kind());

        assertThrows(JsonSyntaxException.class, () -> legacyStrict.fromJson("""
                {orderId: "ORD-1"}""", OrderEvent.class));
    }

    @Test
    void trailingDataAfterTheDocumentIsRejectedEvenByLenientGson() {
        String concatenated = """
                {"orderId": "ORD-1", "status": "PAID"} {"orderId": "ORD-2", "status": "PAID"}
                """;

        JsonSyntaxException failure = assertThrows(JsonSyntaxException.class,
                () -> LEGACY_PARTNERS.fromJson(concatenated, OrderEvent.class));
        assertInstanceOf(MalformedJsonException.class, failure.getCause());
        assertTrue(failure.getMessage().contains("line 1 column 41"), failure.getMessage());

        assertThrows(JsonSyntaxException.class, () -> JsonParser.parseString(concatenated));
    }

    @Test
    void truncatedBodyIsReportedAsMalformed() {
        String truncated = """
                {"orderId": "ORD-3", "status": "PAID", "tags": ["gift",""";

        Rejection rejection = Gateway.ingest(LEGACY_PARTNERS, truncated);

        assertEquals(Rejection.Kind.MALFORMED, rejection.kind());
        assertTrue(rejection.detail().contains("$.tags"), rejection.detail());
    }

    @Test
    void wrongValueTypesAreReportedWithThePathOfTheField() {
        String payload = """
                {"orderId": "ORD-4", "status": "PAID", "amountCents": "forty-two"}
                """;

        JsonSyntaxException failure = assertThrows(JsonSyntaxException.class,
                () -> STRICT_PARTNERS.fromJson(payload, OrderEvent.class));
        assertInstanceOf(NumberFormatException.class, failure.getCause());
        assertTrue(failure.getMessage().contains("forty-two"), failure.getMessage());

        Rejection rejection = Gateway.ingest(STRICT_PARTNERS, """
                {"orderId": "ORD-4", "tags": {"gift": true}}
                """);
        assertEquals(Rejection.Kind.SCHEMA, rejection.kind());
        assertTrue(rejection.detail().contains("$.tags"), rejection.detail());
    }

    @Test
    void emptyBodyMeansNoEventRatherThanAnError() {
        assertNull(LEGACY_PARTNERS.fromJson("", OrderEvent.class));
        assertNull(STRICT_PARTNERS.fromJson("   \n", OrderEvent.class));
        assertNull(LEGACY_PARTNERS.fromJson(new StringReader(""), OrderEvent.class));
        assertEquals(JsonNull.INSTANCE, JsonParser.parseString(""));

        assertEquals(Rejection.Kind.EMPTY, Gateway.ingest(STRICT_PARTNERS, "").kind());
    }

    @Test
    void readerStrictnessIsHonouredAndRestoredWhenGsonHasNone() throws IOException {
        String payload = """
                {orderId: "ORD-5", status: "PAID"}""";

        try (JsonReader reader = new JsonReader(new StringReader(payload))) {
            OrderEvent event = LEGACY_PARTNERS.fromJson(reader, OrderEvent.class);
            assertEquals("ORD-5", event.orderId);
            assertEquals(Strictness.LEGACY_STRICT, reader.getStrictness());
        }

        try (JsonReader reader = new JsonReader(new StringReader(payload))) {
            reader.setStrictness(Strictness.STRICT);
            JsonSyntaxException failure = assertThrows(JsonSyntaxException.class,
                    () -> LEGACY_PARTNERS.fromJson(reader, OrderEvent.class));
            assertInstanceOf(MalformedJsonException.class, failure.getCause());
            assertEquals(Strictness.STRICT, reader.getStrictness());
        }
    }

    @Test
    void parserTreeApiIsLenientForAuditCopies() {
        JsonElement tree = JsonParser.parseString("""
                {orderId: 'ORD-6', amountCents: 10, /* note */ status: PAID}""");

        JsonObject object = tree.getAsJsonObject();
        assertEquals("ORD-6", object.get("orderId").getAsString());
        assertEquals(10, object.get("amountCents").getAsInt());

        OrderEvent event = STRICT_PARTNERS.fromJson(tree, OrderEvent.class);
        assertEquals(Status.PAID, event.status);
        assertEquals("""
                {"orderId":"ORD-6","amountCents":10,"status":"PAID"}""", tree.toString());
    }

    @Test
    void failingResponseSinkIsReportedAsJsonIoException() {
        OrderEvent event = new OrderEvent();
        event.orderId = "ORD-7";
        event.status = Status.PAID;
        event.tags = List.of("gift");
        Writer brokenSink = new BrokenConnectionWriter();

        JsonIOException failure = assertThrows(JsonIOException.class,
                () -> STRICT_PARTNERS.toJson(event, brokenSink));

        assertInstanceOf(IOException.class, failure.getCause());
        assertEquals("connection reset by partner", failure.getCause().getMessage());
    }

    enum Status {
        PAID,
        SHIPPED
    }

    static final class OrderEvent {
        String orderId;
        Status status;
        int amountCents;
        List<String> tags;
    }

    record Rejection(Kind kind, String detail) {

        enum Kind {
            ACCEPTED,
            EMPTY,
            MALFORMED,
            SCHEMA
        }
    }

    /// Maps the outcome of parsing one webhook body to the status the gateway reports back.
    static final class Gateway {

        private Gateway() {
        }

        static Rejection ingest(Gson gson, String body) {
            try {
                OrderEvent event = gson.fromJson(body, OrderEvent.class);
                if (event == null) {
                    return new Rejection(Rejection.Kind.EMPTY, "no event in body");
                }
                return new Rejection(Rejection.Kind.ACCEPTED, event.orderId);
            } catch (JsonSyntaxException e) {
                boolean malformed = e.getCause() instanceof MalformedJsonException
                        || e.getCause() instanceof EOFException;
                Rejection.Kind kind = malformed ? Rejection.Kind.MALFORMED : Rejection.Kind.SCHEMA;
                return new Rejection(kind, e.getMessage());
            }
        }
    }

    /// A response sink whose client hung up before the body was written.
    static final class BrokenConnectionWriter extends Writer {

        @Override
        public void write(char[] buffer, int offset, int length) throws IOException {
            throw new IOException("connection reset by partner");
        }

        @Override
        public void flush() throws IOException {
            throw new IOException("connection reset by partner");
        }

        @Override
        public void close() {
        }
    }
}
