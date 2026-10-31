/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.com.google.gson.Gson;
import com.couchbase.client.core.deps.com.google.gson.GsonBuilder;
import com.couchbase.client.core.deps.com.google.gson.JsonIOException;
import com.couchbase.client.core.deps.com.google.gson.InstanceCreator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ConstructorConstructorTest {
    @Test
    void mapsJson() {
        Gson gson = new Gson();
        Settings settings = gson.fromJson("""
                {"name":"custom"}
                """, Settings.class);
        assertEquals("custom", settings.name);
        assertEquals(11, settings.attempts);
    }

    @Test
    void requiresExplicitCreatorWhenUnsafeIsDisabled() {
        Gson restricted = new GsonBuilder().disableJdkUnsafe().create();
        assertThrows(JsonIOException.class, () -> restricted.fromJson("{}", RequiredSettings.class));
        Gson configured = new GsonBuilder().disableJdkUnsafe()
                .registerTypeAdapter(RequiredSettings.class,
                        (InstanceCreator<RequiredSettings>)
                                type -> new RequiredSettings("default"))
                .create();
        RequiredSettings restored = configured.fromJson("{\"name\":\"configured\"}", RequiredSettings.class);
        assertEquals("configured", restored.name);
    }

    public static class RequiredSettings {
        public String name;

        public RequiredSettings(String name) {
            this.name = name;
        }
    }

    public static class Settings {
        public String name;
        public int attempts;

        public Settings() {
            attempts = 11;
        }
    }
}
