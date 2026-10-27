/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_code_gson.gson;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Gson's JSON tree model to merge deployment settings and export a credential-free configuration.
public class DeploymentConfigurationTreeTest {

    @Test
    @Timeout(60)
    public void overlaysEnvironmentSettingsAndExportsPublicConfiguration() {
        JsonParser parser = new JsonParser();
        JsonObject configuration = parser.parse("""
                {
                  "region": "west",
                  "service": {"name": "inventory", "port": 8080, "tls": false},
                  "features": ["stock"],
                  "credentials": {"token": "private-deployment-token"},
                  "webhook": "https://example.invalid/events"
                }
                """).getAsJsonObject();
        JsonObject environment = parser.parse("""
                {
                  "service": {"port": 8443, "tls": true},
                  "features": ["stock", "audit"],
                  "webhook": null,
                  "labels": {"environment": "production"}
                }
                """).getAsJsonObject();

        overlay(configuration, environment);

        JsonObject service = configuration.getAsJsonObject("service");
        assertEquals("inventory", service.get("name").getAsString());
        assertEquals(8443, service.get("port").getAsInt());
        assertTrue(service.get("tls").getAsBoolean());
        assertEquals(2, configuration.getAsJsonArray("features").size());
        assertEquals("audit", configuration.getAsJsonArray("features").get(1).getAsString());
        assertTrue(configuration.get("webhook").isJsonNull());

        JsonElement credentials = configuration.remove("credentials");
        assertEquals("private-deployment-token",
                credentials.getAsJsonObject().get("token").getAsString());
        Gson gson = new GsonBuilder().serializeNulls().setPrettyPrinting().create();
        String publicConfiguration = gson.toJson(configuration);

        assertFalse(publicConfiguration.contains("private-deployment-token"));
        assertEquals(parser.parse("""
                {
                  "region": "west",
                  "service": {"name": "inventory", "port": 8443, "tls": true},
                  "features": ["stock", "audit"],
                  "webhook": null,
                  "labels": {"environment": "production"}
                }
                """), parser.parse(publicConfiguration));
    }

    private static void overlay(JsonObject configuration, JsonObject overrides) {
        for (Map.Entry<String, JsonElement> entry : overrides.entrySet()) {
            String name = entry.getKey();
            JsonElement replacement = entry.getValue();
            JsonElement existing = configuration.get(name);
            if (existing != null && existing.isJsonObject() && replacement.isJsonObject()) {
                overlay(existing.getAsJsonObject(), replacement.getAsJsonObject());
            } else {
                configuration.add(name, replacement);
            }
        }
    }
}
