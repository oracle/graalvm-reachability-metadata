/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_james.apache_mime4j_core;

import org.apache.james.mime4j.codec.DecoderUtil;
import org.apache.james.mime4j.parser.ContentHandler;
import org.apache.james.mime4j.parser.MimeStreamParser;
import org.apache.james.mime4j.stream.BodyDescriptor;
import org.apache.james.mime4j.stream.Field;
import org.apache.james.mime4j.stream.MimeConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Parses an inbound support ticket's MIME message into text and downloadable attachments.
public class SupportTicketMimeParsingTest {
    @Test
    @Timeout(60)
    public void parsesTicketHeadersBodyAndBase64Attachment() throws Exception {
        String message = """
                From: alerts@example.test
                To: support@example.test
                Subject: =?UTF-8?Q?Support_ticket_=E2=9C=85?=
                MIME-Version: 1.0
                Content-Type: multipart/mixed; boundary="ticket-42"

                --ticket-42
                Content-Type: text/plain; charset=UTF-8
                Content-Transfer-Encoding: quoted-printable

                A customer reported a stale cache.=0AThe request id is abc-17.
                --ticket-42
                Content-Type: application/octet-stream
                Content-Disposition: attachment; filename="diagnostic.txt"
                Content-Transfer-Encoding: base64

                amF2YS5sYW5nLklsbGVnYWxTdGF0ZUV4Y2VwdGlvbjogc3RhbGUgY2FjaGUKcmVxdWVzdC1pZD1hYmMtMTcK
                --ticket-42--
                """;

        TicketMimeHandler handler = new TicketMimeHandler();
        MimeStreamParser parser = new MimeStreamParser(MimeConfig.DEFAULT);
        parser.setContentDecoding(true);
        parser.setContentHandler(handler);
        parser.parse(new ByteArrayInputStream(message.getBytes(StandardCharsets.US_ASCII)));

        assertEquals("alerts@example.test", handler.messageHeaders.get("from"));
        assertEquals("support@example.test", handler.messageHeaders.get("to"));
        assertEquals("Support ticket \u2705",
                DecoderUtil.decodeEncodedWords(handler.messageHeaders.get("subject"), StandardCharsets.UTF_8));
        assertEquals(2, handler.parts.size());

        MimePart textPart = handler.parts.getFirst();
        assertEquals("text/plain", textPart.mimeType);
        assertEquals("A customer reported a stale cache.\nThe request id is abc-17.",
                new String(textPart.body, Charset.forName(textPart.charset)));

        MimePart attachment = handler.parts.get(1);
        assertEquals("application/octet-stream", attachment.mimeType);
        assertEquals("diagnostic.txt", parameter(attachment.headers.get("content-disposition"), "filename"));
        assertTrue(attachment.body.length > 0);
        assertArrayEquals("java.lang.IllegalStateException: stale cache\nrequest-id=abc-17\n"
                        .getBytes(StandardCharsets.US_ASCII), attachment.body);
    }

    private static String parameter(String header, String name) {
        for (String component : header.split(";")) {
            String[] pair = component.trim().split("=", 2);
            if (pair.length == 2 && pair[0].equalsIgnoreCase(name)) {
                return pair[1].trim().replaceAll("^\\\"|\\\"$", "");
            }
        }
        return "";
    }

    private static final class TicketMimeHandler implements ContentHandler {
        private final Map<String, String> messageHeaders = new LinkedHashMap<>();
        private final List<MimePart> parts = new ArrayList<>();
        private Map<String, String> currentPartHeaders;

        @Override
        public void startMessage() {
        }

        @Override
        public void endMessage() {
        }

        @Override
        public void startBodyPart() {
            currentPartHeaders = new LinkedHashMap<>();
        }

        @Override
        public void endBodyPart() {
            currentPartHeaders = null;
        }

        @Override
        public void startHeader() {
        }

        @Override
        public void field(Field field) {
            Map<String, String> headers = currentPartHeaders == null ? messageHeaders : currentPartHeaders;
            headers.put(field.getName().toLowerCase(Locale.ROOT), field.getBody());
        }

        @Override
        public void endHeader() {
        }

        @Override
        public void preamble(InputStream input) throws IOException {
            input.readAllBytes();
        }

        @Override
        public void epilogue(InputStream input) throws IOException {
            input.readAllBytes();
        }

        @Override
        public void startMultipart(BodyDescriptor descriptor) {
        }

        @Override
        public void endMultipart() {
        }

        @Override
        public void body(BodyDescriptor descriptor, InputStream input) throws IOException {
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            input.transferTo(body);
            parts.add(new MimePart(new LinkedHashMap<>(currentPartHeaders), descriptor.getMimeType(),
                    descriptor.getCharset(), body.toByteArray()));
        }

        @Override
        public void raw(InputStream input) throws IOException {
            input.readAllBytes();
        }
    }

    private record MimePart(Map<String, String> headers, String mimeType, String charset, byte[] body) {
    }
}
