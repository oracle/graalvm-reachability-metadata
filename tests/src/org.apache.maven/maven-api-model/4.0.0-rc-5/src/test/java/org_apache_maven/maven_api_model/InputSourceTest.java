/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_api_model;

import org.apache.maven.api.model.InputLocation;
import org.apache.maven.api.model.InputSource;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class InputSourceTest {
    @Test
    void recordsImportOriginAndIdentifiesEquivalentModelSources() {
        InputSource project = new InputSource("com.example:application:1.0", "application/pom.xml");
        InputLocation importLocation = new InputLocation(20, 7, project);
        InputSource imported = new InputSource("com.example:platform:1.0", "platform/pom.xml", importLocation);
        InputSource sameModel = new InputSource("com.example:platform:1.0", "platform/pom.xml");

        assertEquals("com.example:platform:1.0", imported.getModelId());
        assertEquals("platform/pom.xml", imported.getLocation());
        assertSame(importLocation, imported.getImportedFrom());
        assertEquals("application/pom.xml", imported.getImportedFrom().getSource().getLocation());
        assertEquals(imported, sameModel);
        assertEquals(imported.hashCode(), sameModel.hashCode());
        assertEquals(1, new HashSet<>(List.of(imported, sameModel)).size());
    }

    @Test
    void flattensAndDeduplicatesMergedModelOrigins() {
        InputSource parent = new InputSource("com.example:parent:1.0", "parent/pom.xml");
        InputSource child = new InputSource("com.example:child:1.0", "child/pom.xml");
        InputSource merged = InputSource.merge(InputSource.merge(parent, child), parent);
        InputSource expected = new InputSource(new HashSet<>(List.of(parent, child)));

        assertEquals(expected, merged);
        assertEquals(expected.hashCode(), merged.hashCode());
        assertTrue(merged.toString().startsWith("merged["));
        assertTrue(merged.toString().contains(parent.toString()));
        assertTrue(merged.toString().contains(child.toString()));
    }
}
