/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_jsonwebtoken.jjwt_jackson;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.SerializableString;
import com.fasterxml.jackson.core.io.CharacterEscapes;
import com.fasterxml.jackson.core.io.SerializedString;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.jackson.io.JacksonDeserializer;
import io.jsonwebtoken.jackson.io.JacksonSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Jackson's output character escaping for HTML-embedded identity bootstrap JSON.
public class HtmlIdentityBootstrapTest {
    @Test
    @Timeout(60)
    public void embedsUntrustedProfileTextWithoutTerminatingTheJsonScriptElement() {
        String displayName = "Zo\u00eb </script><script>alert(\"profile\")</script> & team";
        String welcome = "Welcome\nUse the \\warehouse\\ console";
        Claims identity = Jwts.claims()
                .issuer("warehouse-identity")
                .subject("operator-42")
                .add("profile", Map.of("display_name", displayName, "welcome", welcome))
                .build();
        JsonFactory factory = new JsonFactory().setCharacterEscapes(new HtmlScriptEscapes());
        JacksonSerializer<Map<String, ?>> serializer = new JacksonSerializer<>(new ObjectMapper(factory));
        String json = new String(serializer.serialize(identity), StandardCharsets.UTF_8);

        // HTML parses script boundaries before interpreting JSON string escapes.
        assertFalse(json.contains("<"));
        assertFalse(json.contains(">"));
        assertFalse(json.contains("&"));
        assertTrue(json.contains("\\u003c/script\\u003e"));
        String prefix = "<script id=\"identity\" type=\"application/json\">";
        String suffix = "</script>";
        String page = prefix + json + suffix;
        int closingTag = page.indexOf(suffix);
        assertEquals(prefix.length() + json.length(), closingTag);
        assertEquals(closingTag, page.lastIndexOf(suffix));

        // A client reads the script's text as JSON, recovering the original profile verbatim.
        byte[] embeddedJson = page.substring(prefix.length(), closingTag).getBytes(StandardCharsets.UTF_8);
        JacksonDeserializer<Map<String, ?>> deserializer = new JacksonDeserializer<>();
        Claims received = Jwts.claims().add(deserializer.deserialize(embeddedJson)).build();
        Map<?, ?> profile = received.get("profile", Map.class);

        assertEquals("warehouse-identity", received.getIssuer());
        assertEquals("operator-42", received.getSubject());
        assertEquals(displayName, profile.get("display_name"));
        assertEquals(welcome, profile.get("welcome"));
        assertEquals(identity, received);
    }

    public static final class HtmlScriptEscapes extends CharacterEscapes {
        private final int[] asciiEscapes = CharacterEscapes.standardAsciiEscapesForJSON();

        public HtmlScriptEscapes() {
            asciiEscapes['<'] = ESCAPE_CUSTOM;
            asciiEscapes['>'] = ESCAPE_CUSTOM;
            asciiEscapes['&'] = ESCAPE_CUSTOM;
        }

        @Override
        public int[] getEscapeCodesForAscii() {
            return asciiEscapes;
        }

        @Override
        public SerializableString getEscapeSequence(int character) {
            return switch (character) {
                case '<' -> new SerializedString("\\u003c");
                case '>' -> new SerializedString("\\u003e");
                case '&' -> new SerializedString("\\u0026");
                default -> null;
            };
        }
    }
}
