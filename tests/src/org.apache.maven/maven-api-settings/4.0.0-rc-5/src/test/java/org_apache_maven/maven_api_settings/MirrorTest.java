/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_api_settings;

import org.apache.maven.api.settings.Mirror;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MirrorTest {
    @Test
    void configuresMirrorSelectionAndDerivesBlockedMirror() {
        Mirror mirror = Mirror.newBuilder().id("company-mirror").name("Company mirror")
                .url("https://mirror.example.test/repository")
                .mirrorOf("*,!internal").mirrorOfLayouts("default").build();
        Mirror blocked = mirror.withBlocked(true);

        assertEquals("company-mirror", mirror.getId());
        assertEquals("Company mirror", mirror.getName());
        assertEquals("https://mirror.example.test/repository", mirror.getUrl());
        assertEquals("*,!internal", mirror.getMirrorOf());
        assertEquals("default", mirror.getLayout());
        assertEquals("default", mirror.getMirrorOfLayouts());
        assertFalse(mirror.isBlocked());
        assertTrue(blocked.isBlocked());
        assertEquals(mirror.getId(), blocked.getId());
        assertEquals(mirror.getUrl(), blocked.getUrl());
        assertEquals(mirror.getMirrorOf(), blocked.getMirrorOf());
        assertEquals(mirror.getMirrorOfLayouts(), blocked.getMirrorOfLayouts());
    }

    @Test
    void usesDefaultLayoutsUnlessExplicitlyOverridden() {
        Mirror defaults = Mirror.newInstance();
        Mirror legacy = defaults.withLayout("legacy");

        assertEquals("default", defaults.getLayout());
        assertEquals("default,legacy", defaults.getMirrorOfLayouts());
        assertFalse(defaults.isBlocked());
        assertEquals("legacy", legacy.getLayout());
        assertEquals(defaults.getMirrorOfLayouts(), legacy.getMirrorOfLayouts());
    }
}
