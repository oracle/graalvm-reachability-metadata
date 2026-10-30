/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_api_core;

import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.apache.maven.api.ProtoSession;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ProtoSessionTest {
    @Test
    void snapshotsPropertiesAndOverlaysUserValues() {
        Map<String, String> user = new HashMap<>(Map.of("profile", "user", "user-only", "enabled"));
        Map<String, String> system = new HashMap<>(Map.of("profile", "system", "system-only", "enabled"));
        Path root = Path.of("workspace");
        Path top = root.resolve("application");
        Instant start = Instant.parse("2025-01-01T00:00:00Z");
        ProtoSession session = ProtoSession.newBuilder()
                .withUserProperties(user)
                .withSystemProperties(system)
                .withStartTime(start)
                .withTopDirectory(top)
                .withRootDirectory(root)
                .build();
        user.put("profile", "changed");
        system.clear();

        assertEquals(Map.of("profile", "user", "user-only", "enabled"), session.getUserProperties());
        assertEquals(Map.of("profile", "system", "system-only", "enabled"), session.getSystemProperties());
        assertEquals(Map.of("profile", "user", "user-only", "enabled", "system-only", "enabled"),
                session.getEffectiveProperties());
        assertEquals(start, session.getStartTime());
        assertEquals(top, session.getTopDirectory());
        assertEquals(root, session.getRootDirectory());
        assertThrows(UnsupportedOperationException.class, () -> session.getEffectiveProperties().put("new", "value"));
        assertThrows(UnsupportedOperationException.class, () -> session.getUserProperties().clear());
        assertThrows(UnsupportedOperationException.class, () -> session.getSystemProperties().clear());

        ProtoSession copy = session.toBuilder().withUserProperties(Map.of("profile", "child")).build();
        assertEquals("child", copy.getEffectiveProperties().get("profile"));
        assertEquals("enabled", copy.getEffectiveProperties().get("system-only"));
        assertEquals(start, copy.getStartTime());
        assertEquals(top, copy.getTopDirectory());
        assertEquals(root, copy.getRootDirectory());
        assertEquals("user", session.getEffectiveProperties().get("profile"));
    }

    @Test
    void copiesSessionsWhoseRootDirectoryIsUnknown() {
        ProtoSession session = ProtoSession.newBuilder()
                .withUserProperties(Map.of())
                .withSystemProperties(Map.of())
                .withTopDirectory(Path.of("project"))
                .build();

        assertThrows(IllegalStateException.class, session::getRootDirectory);
        ProtoSession copy = session.toBuilder().build();
        assertEquals(session.getStartTime(), copy.getStartTime());
        assertEquals(session.getTopDirectory(), copy.getTopDirectory());
        assertThrows(IllegalStateException.class, copy::getRootDirectory);
    }
}
