/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_api_toolchain;

import org.apache.maven.api.toolchain.InputLocation;
import org.apache.maven.api.toolchain.InputSource;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class InputLocationTest {
    @Test
    void tracksSourceCoordinatesAndSnapshotsNestedLocations() {
        InputSource source = new InputSource("toolchains.xml");
        InputLocation type = new InputLocation(5, 9, source);
        Map<Object, InputLocation> fields = new HashMap<>();
        fields.put("type", type);

        InputLocation toolchain = new InputLocation(4, 3, source, fields);
        fields.clear();

        assertEquals(4, toolchain.getLineNumber());
        assertEquals(3, toolchain.getColumnNumber());
        assertSame(source, toolchain.getSource());
        assertSame(type, toolchain.getLocation("type"));
        assertNull(toolchain.getLocation("configuration"));
        assertEquals(Map.of("type", type), toolchain.getLocations());
        assertThrows(UnsupportedOperationException.class, () -> toolchain.getLocations().put("other", type));
    }

    @Test
    void supportsSelfLocationsAndUnknownCoordinates() {
        InputSource source = new InputSource("toolchains.xml");
        InputLocation document = new InputLocation(source);
        InputLocation field = new InputLocation(7, 4, source, "type");
        InputLocation coordinatesOnly = new InputLocation(2, 8);

        assertEquals(-1, document.getLineNumber());
        assertEquals(-1, document.getColumnNumber());
        assertSame(source, document.getSource());
        assertSame(document, document.getLocation(0));
        assertSame(field, field.getLocation("type"));
        assertEquals(2, coordinatesOnly.getLineNumber());
        assertEquals(8, coordinatesOnly.getColumnNumber());
        assertNull(coordinatesOnly.getSource());
        assertTrue(coordinatesOnly.getLocations().isEmpty());
    }

    @Test
    void mergesFieldLocationsWithExplicitDominance() {
        InputSource targetSource = new InputSource("project-toolchains.xml");
        InputSource sourceSource = new InputSource("user-toolchains.xml");
        InputLocation targetType = new InputLocation(3, 5, targetSource);
        InputLocation sourceType = new InputLocation(8, 5, sourceSource);
        InputLocation configuration = new InputLocation(9, 5, sourceSource);
        InputLocation provides = new InputLocation(4, 5, targetSource);
        InputLocation target = new InputLocation(2, 1, targetSource,
                Map.of("type", targetType, "provides", provides));
        InputLocation source = new InputLocation(7, 1, sourceSource,
                Map.of("type", sourceType, "configuration", configuration));

        InputLocation sourceDominant = InputLocation.merge(target, source, true);
        InputLocation targetDominant = InputLocation.merge(target, source, false);

        assertSame(sourceType, sourceDominant.getLocation("type"));
        assertSame(targetType, targetDominant.getLocation("type"));
        assertSame(provides, sourceDominant.getLocation("provides"));
        assertSame(configuration, targetDominant.getLocation("configuration"));
        assertSame(targetSource, sourceDominant.getSource());
        assertEquals(2, sourceDominant.getLineNumber());
        assertEquals(1, sourceDominant.getColumnNumber());
        assertSame(targetType, target.getLocation("type"));
        assertSame(sourceType, source.getLocation("type"));
        assertSame(target, InputLocation.merge(target, null, true));
        assertSame(source, InputLocation.merge(null, source, false));
    }

    @Test
    void mergesListLocationsInRequestedIndexOrder() {
        InputSource targetSource = new InputSource("project-toolchains.xml");
        InputLocation first = new InputLocation(4, 3, targetSource);
        InputLocation second = new InputLocation(8, 3, targetSource);
        InputLocation imported = new InputLocation(6, 3, new InputSource("user-toolchains.xml"));
        InputLocation target = new InputLocation(2, 1, targetSource, Map.of(0, first, 1, second));
        InputLocation source = new InputLocation(3, 1, imported.getSource(), Map.of(0, imported));

        InputLocation merged = InputLocation.merge(target, source, List.of(1, ~0, 0));

        assertSame(second, merged.getLocation(0));
        assertSame(imported, merged.getLocation(1));
        assertSame(first, merged.getLocation(2));
        assertEquals(3, merged.getLocations().size());
        assertSame(targetSource, merged.getSource());
        assertEquals(2, merged.getLineNumber());
        assertSame(target, InputLocation.merge(target, null, List.of(0)));
        assertSame(source, InputLocation.merge(null, source, List.of(0)));
    }
}
