/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_api_settings;

import org.apache.maven.api.settings.Activation;
import org.apache.maven.api.settings.Profile;
import org.apache.maven.api.settings.Repository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProfileTest {
    @Test
    void snapshotsPropertiesAndRepositoryLists() {
        Map<String, String> properties = new HashMap<>();
        properties.put("environment", "staging");
        properties.put("region", "west");
        properties.put("build.mode", "verified");
        Repository releases = Repository.newBuilder().id("releases")
                .url("https://repo.example.test/releases").build();
        Repository plugins = Repository.newBuilder().id("plugins")
                .url("https://repo.example.test/plugins").build();
        List<Repository> repositories = new ArrayList<>(List.of(releases, plugins));
        Activation activation = Activation.newBuilder().activeByDefault(true).build();
        Profile profile = Profile.newBuilder().id("staging")
                .activation(activation)
                .properties(properties)
                .repositories(repositories)
                .pluginRepositories(List.of(plugins))
                .build();
        properties.put("environment", "production");
        repositories.clear();

        assertEquals("staging", profile.getId());
        assertSame(activation, profile.getActivation());
        assertEquals(Map.of("environment", "staging", "region", "west", "build.mode", "verified"),
                profile.getProperties());
        assertEquals(List.of(releases, plugins), profile.getRepositories());
        assertEquals(List.of(plugins), profile.getPluginRepositories());
        assertThrows(UnsupportedOperationException.class, () -> profile.getProperties().put("extra", "value"));
        assertThrows(UnsupportedOperationException.class, () -> profile.getRepositories().clear());

        Profile renamed = profile.withId("integration");
        assertEquals("integration", renamed.getId());
        assertEquals("staging", profile.getId());
        assertEquals(profile.getProperties(), renamed.getProperties());
        assertEquals(profile.getRepositories(), renamed.getRepositories());
        assertSame(activation, renamed.getActivation());
    }

    @Test
    void replacesPropertiesWithoutMutatingBaseProfile() {
        Profile original = Profile.newBuilder().id("local").properties(Map.of("mode", "fast")).build();
        Profile updated = original.withProperties(Map.of("mode", "thorough"));

        assertEquals(Map.of("mode", "fast"), original.getProperties());
        assertEquals(Map.of("mode", "thorough"), updated.getProperties());
        assertEquals("local", updated.getId());
        assertTrue(Profile.newInstance().getProperties().isEmpty());
        assertTrue(Profile.newInstance().getRepositories().isEmpty());
    }
}
