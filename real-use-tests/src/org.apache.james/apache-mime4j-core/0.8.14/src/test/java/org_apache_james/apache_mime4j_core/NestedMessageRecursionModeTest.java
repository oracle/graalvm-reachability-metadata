/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_james.apache_mime4j_core;

import org.apache.james.mime4j.parser.AbstractContentHandler;
import org.apache.james.mime4j.parser.MimeStreamParser;
import org.apache.james.mime4j.stream.BodyDescriptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Controls recursion into attached message/rfc822 entities for a mail archive.
public class NestedMessageRecursionModeTest {
    @Test
    @Timeout(60)
    public void indexesNestedMessageWhenConfiguredToRecurseAndPreservesItOtherwise() throws Exception {
        String forwardedMessage = "Content-Type: message/rfc822\r\n"
                + "Content-Disposition: attachment; filename=\"forwarded.eml\"\r\n"
                + "\r\n"
                + "From: nested@example.test\r\n"
                + "Content-Type: text/plain\r\n"
                + "\r\n"
                + "Nested body\r\n";

        ParseEvents recursive = parse(forwardedMessage, true);
        assertEquals(List.of("startMessage", "startMessage", "body:text/plain:Nested body\r\n",
                        "endMessage", "endMessage"), recursive.events());

        ParseEvents opaque = parse(forwardedMessage, false);
        assertEquals(List.of("startMessage", "body:message/rfc822:"
                        + "From: nested@example.test\r\n"
                        + "Content-Type: text/plain\r\n"
                        + "\r\n"
                        + "Nested body\r\n", "endMessage"), opaque.events());
    }

    private static ParseEvents parse(String message, boolean recurse) throws Exception {
        EventCollector collector = new EventCollector();
        MimeStreamParser parser = new MimeStreamParser();
        if (recurse) {
            parser.setRecurse();
        } else {
            parser.setNoRecurse();
        }
        parser.setContentHandler(collector);
        parser.parse(new ByteArrayInputStream(message.getBytes(StandardCharsets.US_ASCII)));
        return new ParseEvents(collector.events);
    }

    private record ParseEvents(List<String> events) {
    }

    private static final class EventCollector extends AbstractContentHandler {
        private final List<String> events = new ArrayList<>();

        @Override
        public void startMessage() {
            events.add("startMessage");
        }

        @Override
        public void endMessage() {
            events.add("endMessage");
        }

        @Override
        public void body(BodyDescriptor descriptor, InputStream input) throws IOException {
            events.add("body:" + descriptor.getMimeType() + ":"
                    + new String(input.readAllBytes(), StandardCharsets.US_ASCII));
        }
    }
}
