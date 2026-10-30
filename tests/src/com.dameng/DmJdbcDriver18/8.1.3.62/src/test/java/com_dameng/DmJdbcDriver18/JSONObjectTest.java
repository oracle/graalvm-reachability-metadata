/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_dameng.DmJdbcDriver18;

import dm.jdbc.filter.stat.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.AbstractMap.SimpleEntry;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;

public class JSONObjectTest {
    @Test
    void convertsBeanGettersToJsonProperties() throws Exception {
        JSONObject json = new JSONObject(new ConnectionSettings());
        assertEquals("warehouse", json.getString("name"));
        assertTrue(json.getBoolean("enabled"));
        assertFalse(json.has("class"));
        assertEquals("warehouse", new JSONObject(json.toString()).getString("name"));
    }

    @Test
    void convertsBootstrapLoadedBean() throws Exception {
        JSONObject json = new JSONObject(new SimpleEntry<>("database", "warehouse"));
        assertEquals("database", json.getString("key"));
        assertEquals("warehouse", json.getString("value"));
    }

    @Test
    void selectsPublicFieldsByName() throws Exception {
        PublicSettings settings = new PublicSettings();
        JSONObject json = new JSONObject(settings, new String[]{"host", "port"});
        assertEquals("localhost", json.getString("host"));
        assertEquals(5236, json.getInt("port"));
        assertThat(JSONObject.getNames(settings)).containsExactlyInAnyOrder("host", "port");
    }

    @Test
    void loadsDriverMessagesAsNestedJson() throws Exception {
        JSONObject messages = new JSONObject("dm.jdbc.resource.messages", Locale.ENGLISH);
        assertEquals("EC_INVALID_DB_NAME", messages.getJSONObject("error").getString("invalidDbName"));
        assertEquals("Warning:Empty resultset", messages.getJSONObject("warning").getString("emptyResult"));
    }

    public static class ConnectionSettings {
        public String getName() {
            return "warehouse";
        }

        public boolean isEnabled() {
            return true;
        }
    }

    public static class PublicSettings {
        public String host = "localhost";
        public int port = 5236;
    }
}
