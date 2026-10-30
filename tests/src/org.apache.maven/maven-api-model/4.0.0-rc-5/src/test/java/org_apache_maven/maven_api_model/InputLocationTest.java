/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_api_model;

import org.apache.maven.api.model.Dependency;
import org.apache.maven.api.model.InputLocation;
import org.apache.maven.api.model.InputSource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class InputLocationTest {
    @Test
    void tracksDependencyFieldLocationAcrossVersionUpdate() {
        InputSource source = new InputSource("com.example:application:1.0", "application/pom.xml");
        InputLocation location = new InputLocation(12, 9, source);
        Dependency dependency = Dependency.newBuilder().groupId("com.example").artifactId("api")
                .version("1.0").location("artifactId", location).build();
        Dependency updated = dependency.withVersion("2.0");

        InputLocation tracked = updated.getLocation("artifactId");
        assertEquals(12, tracked.getLineNumber());
        assertEquals(9, tracked.getColumnNumber());
        assertEquals("com.example:application:1.0", tracked.getSource().getModelId());
        assertEquals("application/pom.xml @ 12:9", tracked.toString());
        assertEquals(dependency.getLocationKeys(), updated.getLocationKeys());
        assertNull(updated.getLocation("scope"));
    }

    @Test
    void mergesFieldLocationsWithExplicitDominance() {
        InputSource parentSource = new InputSource("com.example:parent:1.0", "parent/pom.xml");
        InputSource childSource = new InputSource("com.example:child:1.0", "child/pom.xml");
        InputLocation parentVersion = new InputLocation(4, 3, parentSource);
        InputLocation childVersion = new InputLocation(8, 5, childSource);
        InputLocation childScope = new InputLocation(9, 5, childSource);
        InputLocation parent = new InputLocation(1, 1, parentSource, Map.of("version", parentVersion));
        InputLocation child = new InputLocation(1, 1, childSource,
                Map.of("version", childVersion, "scope", childScope));

        InputLocation sourceDominant = InputLocation.merge(parent, child, true);
        InputLocation targetDominant = InputLocation.merge(parent, child, false);

        assertSame(childVersion, sourceDominant.getLocation("version"));
        assertSame(parentVersion, targetDominant.getLocation("version"));
        assertSame(childScope, targetDominant.getLocation("scope"));
        assertSame(parentVersion, parent.getLocation("version"));
        assertEquals(-1, sourceDominant.getLineNumber());
        assertSame(parent, InputLocation.merge(parent, null, true));
        assertSame(child, InputLocation.merge(null, child, false));
    }

    @Test
    void mergesListLocationsUsingSelectedSourceAndTargetIndices() {
        InputSource parentSource = new InputSource("com.example:parent:1.0", "parent/pom.xml");
        InputSource childSource = new InputSource("com.example:child:1.0", "child/pom.xml");
        InputLocation parentItem = new InputLocation(5, 3, parentSource);
        InputLocation childItem = new InputLocation(10, 3, childSource);
        InputLocation parent = new InputLocation(4, 1, parentSource, Map.of(0, parentItem));
        InputLocation child = new InputLocation(9, 1, childSource, Map.of(0, childItem));

        InputLocation merged = InputLocation.merge(parent, child, List.of(-1, 0));
        InputLocation copy = new InputLocation(merged);

        assertEquals(2, merged.getLocations().size());
        assertSame(childItem, merged.getLocation(0));
        assertSame(parentItem, merged.getLocation(1));
        assertEquals(merged.getLocations(), copy.getLocations());
        assertEquals(merged.getSource(), copy.getSource());
    }
}
