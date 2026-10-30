/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_api_toolchain;

import org.apache.maven.api.toolchain.InputLocation;
import org.apache.maven.api.toolchain.InputLocationTracker;
import org.apache.maven.api.toolchain.InputSource;
import org.apache.maven.api.toolchain.ToolchainModel;
import org.apache.maven.api.toolchain.TrackableBase;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TrackableBaseTest {
    @Test
    void retainsFieldLocationsAndImportOriginInImmutableSnapshot() {
        InputSource source = new InputSource("toolchains.xml");
        InputLocation type = new InputLocation(5, 7, source);
        InputLocation importedFrom = new InputLocation(1, 1, new InputSource("settings.xml"));
        TrackableBase.Builder builder = TrackableBase.newBuilder()
                .location("type", type)
                .location("unknown", null)
                .importedFrom(importedFrom);
        TrackableBase model = builder.build();
        builder.location("configuration", new InputLocation(9, 7, source));
        InputLocationTracker tracker = model;

        assertSame(type, tracker.getLocation("type"));
        assertNull(tracker.getLocation("configuration"));
        assertNull(tracker.getLocation("unknown"));
        assertSame(importedFrom, model.getImportedFrom());
        assertEquals(Set.of("type"), model.getLocationKeys());
        assertThrows(UnsupportedOperationException.class, () -> model.getLocationKeys().clear());
        assertThrows(NullPointerException.class, () -> tracker.getLocation(null));
    }

    @Test
    void derivedToolchainMergesNewLocationsAndKeepsUnchangedFields() {
        InputSource source = new InputSource("toolchains.xml");
        InputLocation oldType = new InputLocation(3, 5, source);
        InputLocation provides = new InputLocation(4, 5, source);
        InputLocation newType = new InputLocation(9, 5, source);
        InputLocation configuration = new InputLocation(10, 5, source);
        ToolchainModel original = ToolchainModel.newBuilder()
                .type("jdk")
                .location("type", oldType)
                .location("provides", provides)
                .build();

        ToolchainModel derived = original.with()
                .type("compiler")
                .location("type", newType)
                .location("configuration", configuration)
                .build();

        assertSame(newType, derived.getLocation("type"));
        assertSame(provides, derived.getLocation("provides"));
        assertSame(configuration, derived.getLocation("configuration"));
        assertEquals(Set.of("type", "provides", "configuration"), derived.getLocationKeys());
        assertSame(oldType, original.getLocation("type"));
        assertNull(original.getLocation("configuration"));
    }

    @Test
    void emptyModelHasNoLocationsOrImportOrigin() {
        TrackableBase model = TrackableBase.newInstance();

        assertTrue(model.getLocationKeys().isEmpty());
        assertNull(model.getLocation("type"));
        assertNull(model.getImportedFrom());
        assertTrue(TrackableBase.newInstance(false).getLocationKeys().isEmpty());
    }
}
