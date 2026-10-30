/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_api_settings;

import org.apache.maven.api.settings.Mirror;
import org.apache.maven.api.settings.Profile;
import org.apache.maven.api.settings.Proxy;
import org.apache.maven.api.settings.Repository;
import org.apache.maven.api.settings.Server;
import org.apache.maven.api.settings.Settings;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SettingsTest {
    @Test
    void suppliesDefaultsAndEmptyCollections() {
        Settings settings = Settings.newInstance();

        assertEquals("UTF-8", settings.getModelEncoding());
        assertTrue(settings.isInteractiveMode());
        assertFalse(settings.isOffline());
        assertFalse(settings.isUsePluginRegistry());
        assertTrue(settings.getProxies().isEmpty());
        assertTrue(settings.getServers().isEmpty());
        assertTrue(settings.getMirrors().isEmpty());
        assertTrue(settings.getRepositories().isEmpty());
        assertTrue(settings.getPluginRepositories().isEmpty());
        assertTrue(settings.getProfiles().isEmpty());
        assertTrue(settings.getActiveProfiles().isEmpty());
        assertTrue(settings.getPluginGroups().isEmpty());
    }

    @Test
    void assemblesSettingsAndCopiesWithoutChangingTheOriginal() {
        Proxy proxy = Proxy.newBuilder().id("office").host("proxy.example.test").build();
        Server server = Server.newBuilder().id("releases").username("publisher").build();
        Mirror mirror = Mirror.newBuilder().id("mirror").mirrorOf("*")
                .url("https://mirror.example.test").build();
        Repository repository = Repository.newBuilder().id("releases")
                .url("https://repo.example.test/releases").build();
        Profile profile = Profile.newBuilder().id("office").repositories(List.of(repository)).build();
        List<String> activeProfiles = new ArrayList<>(List.of("office", "integration"));
        Settings settings = Settings.newBuilder()
                .localRepository("local-cache")
                .interactiveMode(false)
                .usePluginRegistry(true)
                .proxies(List.of(proxy))
                .servers(List.of(server))
                .mirrors(List.of(mirror))
                .repositories(List.of(repository))
                .pluginRepositories(List.of(repository))
                .profiles(List.of(profile))
                .activeProfiles(activeProfiles)
                .pluginGroups(List.of("com.example.plugins", "com.example.tools"))
                .build();
        activeProfiles.clear();

        Settings offline = settings.withOffline(true);

        assertEquals("local-cache", offline.getLocalRepository());
        assertFalse(offline.isInteractiveMode());
        assertTrue(offline.isUsePluginRegistry());
        assertTrue(offline.isOffline());
        assertFalse(settings.isOffline());
        assertEquals(List.of(proxy), offline.getProxies());
        assertEquals(List.of(server), offline.getServers());
        assertEquals(List.of(mirror), offline.getMirrors());
        assertEquals(List.of(repository), offline.getRepositories());
        assertEquals(List.of(repository), offline.getPluginRepositories());
        assertEquals(List.of(profile), offline.getProfiles());
        assertEquals(List.of("office", "integration"), offline.getActiveProfiles());
        assertEquals(List.of("com.example.plugins", "com.example.tools"), offline.getPluginGroups());
        assertSame(settings, settings.with().build());
        assertThrows(UnsupportedOperationException.class, () -> settings.getActiveProfiles().add("extra"));
        assertThrows(UnsupportedOperationException.class, () -> settings.getServers().clear());
    }

    @Test
    void reusingBuilderKeepsPreviouslyBuiltSettingsUnchanged() {
        Settings.Builder builder = Settings.newBuilder().activeProfiles(List.of("development"));
        Settings development = builder.build();
        Settings production = builder.activeProfiles(List.of("production")).offline(true).build();

        assertEquals(List.of("development"), development.getActiveProfiles());
        assertFalse(development.isOffline());
        assertEquals(List.of("production"), production.getActiveProfiles());
        assertTrue(production.isOffline());
    }
}
