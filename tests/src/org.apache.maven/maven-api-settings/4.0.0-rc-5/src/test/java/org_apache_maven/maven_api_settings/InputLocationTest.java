/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_api_settings;

import org.apache.maven.api.settings.InputLocation;
import org.apache.maven.api.settings.InputSource;
import org.apache.maven.api.settings.Settings;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class InputLocationTest {
    @Test
    void tracksSettingsFieldsAndImportOrigin() {
        InputSource source = new InputSource("user-settings.xml");
        InputLocation repositoryLocation = new InputLocation(7, 5, source);
        InputLocation importLocation = new InputLocation(source);
        Settings settings = Settings.newBuilder()
                .localRepository("local-cache")
                .location("localRepository", repositoryLocation)
                .importedFrom(importLocation)
                .build();

        assertSame(repositoryLocation, settings.getLocation("localRepository"));
        assertEquals(7, settings.getLocation("localRepository").getLineNumber());
        assertEquals(5, settings.getLocation("localRepository").getColumnNumber());
        assertSame(source, settings.getLocation("localRepository").getSource());
        assertEquals("user-settings.xml", source.getLocation());
        assertEquals("user-settings.xml", source.toString());
        assertEquals(List.of("localRepository"), List.copyOf(settings.getLocationKeys()));
        assertSame(importLocation, settings.getImportedFrom());
        assertSame(importLocation, importLocation.getLocation(0));
        assertEquals(-1, importLocation.getLineNumber());
        assertNull(settings.getLocation("offline"));
        assertThrows(NullPointerException.class, () -> settings.getLocation(null));
    }

    @Test
    void mergesFieldLocationsWithExplicitDominance() {
        InputSource user = new InputSource("user-settings.xml");
        InputSource global = new InputSource("global-settings.xml");
        InputLocation userRepository = new InputLocation(4, 3, user);
        InputLocation globalRepository = new InputLocation(8, 3, global);
        InputLocation globalOffline = new InputLocation(9, 3, global);
        InputLocation target = new InputLocation(1, 1, user, Map.of("localRepository", userRepository));
        InputLocation source = new InputLocation(2, 1, global,
                Map.of("localRepository", globalRepository, "offline", globalOffline));

        InputLocation targetDominant = InputLocation.merge(target, source, false);
        InputLocation sourceDominant = InputLocation.merge(target, source, true);

        assertSame(userRepository, targetDominant.getLocation("localRepository"));
        assertSame(globalRepository, sourceDominant.getLocation("localRepository"));
        assertSame(globalOffline, targetDominant.getLocation("offline"));
        assertSame(globalOffline, sourceDominant.getLocation("offline"));
        assertEquals(1, sourceDominant.getLineNumber());
        assertEquals(1, sourceDominant.getColumnNumber());
        assertSame(user, sourceDominant.getSource());
        assertSame(userRepository, target.getLocation("localRepository"));
        assertSame(target, InputLocation.merge(target, null, true));
        assertSame(source, InputLocation.merge(null, source, false));
    }

    @Test
    void remapsListElementLocationsInMergedOrder() {
        InputLocation first = new InputLocation(3, 2);
        InputLocation second = new InputLocation(4, 2);
        InputLocation inherited = new InputLocation(12, 2);
        InputLocation target = new InputLocation(1, 1, null, Map.of(0, first, 1, second));
        InputLocation source = new InputLocation(10, 1, null, Map.of(0, inherited));

        InputLocation merged = InputLocation.merge(target, source, List.of(1, ~0, 0));

        assertEquals(3, merged.getLocations().size());
        assertSame(second, merged.getLocation(0));
        assertSame(inherited, merged.getLocation(1));
        assertSame(first, merged.getLocation(2));
        assertSame(target, InputLocation.merge(target, null, List.of(0)));
        assertSame(source, InputLocation.merge(null, source, List.of(0)));
        assertThrows(UnsupportedOperationException.class, () -> merged.getLocations().clear());
    }
}
