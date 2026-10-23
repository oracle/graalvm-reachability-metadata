/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_jsonwebtoken.jjwt_jackson;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.jackson.io.JacksonDeserializer;
import io.jsonwebtoken.jackson.io.JacksonSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Jackson's stream-based JSON codec for compressed JSON Lines claim audit exports.
public class CompressedClaimAuditExportTest {
    @Test
    @Timeout(60)
    public void exportsAndReplaysAuthorizationChanges(@TempDir Path directory) throws IOException {
        Claims grant = Jwts.claims()
                .id("audit-101")
                .issuer("access-management")
                .subject("operator-42")
                .add("event", "access-granted")
                .add("active", true)
                .add("roles", List.of("inventory:read", "shipment:create"))
                .add("reason", "Approved by Zo\u00eb\nTicket: OPS-704")
                .build();
        Claims revocation = Jwts.claims()
                .id("audit-102")
                .issuer("access-management")
                .subject("operator-42")
                .add("event", "access-revoked")
                .add("active", false)
                .add("reason", "Contract ended")
                .build();
        List<Claims> changes = List.of(grant, revocation);
        JacksonSerializer<Map<String, ?>> serializer = new JacksonSerializer<>();
        Path archive = directory.resolve("authorization-audit.jsonl.gz");

        try (GZIPOutputStream output = new GZIPOutputStream(Files.newOutputStream(archive))) {
            for (Claims change : changes) {
                serializer.serialize(change, output);
                output.write('\n');
            }
        }

        JacksonDeserializer<Map<String, ?>> deserializer = new JacksonDeserializer<>();
        List<Claims> replayed = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new GZIPInputStream(Files.newInputStream(archive)), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Map<String, ?> values = deserializer.deserialize(new StringReader(line));
                replayed.add(Jwts.claims().add(values).build());
            }
        }

        assertEquals(changes, replayed);
        assertEquals(2, replayed.size());
        assertEquals("Approved by Zo\u00eb\nTicket: OPS-704", replayed.get(0).get("reason"));
        assertEquals("access-revoked", replayed.get(1).get("event"));
        assertEquals(Boolean.FALSE, replayed.get(1).get("active", Boolean.class));
    }
}
