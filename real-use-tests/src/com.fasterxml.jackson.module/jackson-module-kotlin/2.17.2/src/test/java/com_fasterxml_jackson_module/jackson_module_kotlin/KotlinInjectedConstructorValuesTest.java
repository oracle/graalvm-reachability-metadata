/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_module.jackson_module_kotlin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.annotation.JacksonInject;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.InjectableValues;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.module.kotlin.KotlinModule;
import java.util.List;
import kotlin.Triple;
import org.junit.jupiter.api.Test;

/// Server-side values injected into Kotlin constructor parameters through `@JacksonInject` and
/// `InjectableValues` by the Kotlin module's value instantiator.
///
/// A Java chat backend receives messages from Kotlin clients, which model each message as a
/// Kotlin `Triple` of sender, text, and receive time. A client sends only the text: the
/// backend binds the sender from the authenticated session and the receive time from its own
/// clock by injecting both into the `Triple` constructor, and it ignores any sender a client
/// writes into the payload so that no client can post in another user's name. The backend
/// then archives the completed messages as plain JSON.
class KotlinInjectedConstructorValuesTest {

    private static final long RECEIVED_AT = 1_760_000_000_000L;

    private static final ObjectMapper INBOUND = new ObjectMapper()
            .registerModule(new KotlinModule.Builder().build())
            .addMixIn(Triple.class, InboundMessage.class);

    private static final ObjectMapper ARCHIVE = new ObjectMapper()
            .registerModule(new KotlinModule.Builder().build());

    private static final TypeReference<Triple<String, String, Long>> MESSAGE =
            new TypeReference<Triple<String, String, Long>>() {
            };

    private static final TypeReference<List<Triple<String, String, Long>>> MESSAGE_BATCH =
            new TypeReference<List<Triple<String, String, Long>>>() {
            };

    /// Binds the sender and receive time of an inbound message to the session's injectables and
    /// drops any sender the payload carries.
    @JsonIgnoreProperties("first")
    abstract static class InboundMessage {
        InboundMessage(
                @JacksonInject("sender") Object first,
                Object second,
                @JacksonInject("receivedAt") Object third) {
        }
    }

    private static ObjectReader sessionReader(String user) {
        InjectableValues session = new InjectableValues.Std()
                .addValue("sender", user)
                .addValue("receivedAt", RECEIVED_AT);
        return INBOUND.readerFor(MESSAGE).with(session);
    }

    @Test
    void bindsSenderAndReceiveTimeFromTheSession() throws Exception {
        String payload = """
                {"second": "standup moved to 10:30"}
                """;

        Triple<String, String, Long> fromAlice = sessionReader("alice").readValue(payload);
        Triple<String, String, Long> fromBob = sessionReader("bob").readValue(payload);

        assertEquals(new Triple<>("alice", "standup moved to 10:30", RECEIVED_AT), fromAlice);
        assertEquals("bob", fromBob.getFirst());
        assertEquals(fromAlice.getSecond(), fromBob.getSecond());
        assertEquals(RECEIVED_AT, fromBob.getThird());
    }

    @Test
    void ignoresSenderSpoofedInThePayload() throws Exception {
        String payload = """
                {"first": "admin", "second": "please reset my password"}
                """;

        Triple<String, String, Long> message = sessionReader("mallory").readValue(payload);

        assertEquals("mallory", message.getFirst());
        assertEquals("please reset my password", message.getSecond());
    }

    @Test
    void injectsEveryMessageOfABatchAndArchivesThem() throws Exception {
        InjectableValues session = new InjectableValues.Std()
                .addValue("sender", "carol")
                .addValue("receivedAt", RECEIVED_AT);
        String batch = """
                [{"second": "draft is ready"}, {"second": "review by Friday"}]
                """;

        List<Triple<String, String, Long>> messages =
                INBOUND.readerFor(MESSAGE_BATCH).with(session).readValue(batch);

        assertEquals(List.of(
                new Triple<>("carol", "draft is ready", RECEIVED_AT),
                new Triple<>("carol", "review by Friday", RECEIVED_AT)), messages);

        String archived = ARCHIVE.writeValueAsString(messages);
        JsonNode tree = ARCHIVE.readTree(archived);
        assertEquals("carol", tree.get(1).get("first").asText());
        assertEquals("review by Friday", tree.get(1).get("second").asText());
        assertEquals(RECEIVED_AT, tree.get(1).get("third").longValue());

        assertEquals(messages, ARCHIVE.readValue(archived, MESSAGE_BATCH));
    }

    @Test
    void rejectsMessagesReadOutsideASession() {
        JsonMappingException rejected = assertThrows(JsonMappingException.class,
                () -> INBOUND.readValue("{\"second\": \"hello\"}", MESSAGE));

        assertTrue(rejected.getMessage().contains("sender"), rejected.getMessage());
    }
}
