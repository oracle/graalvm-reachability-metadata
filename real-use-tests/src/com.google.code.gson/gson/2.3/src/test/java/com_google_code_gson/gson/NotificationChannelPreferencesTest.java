/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_code_gson.gson;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/// Exercises Gson's enum-binding subsystem to import notification preferences and persist a channel opt-out.
public class NotificationChannelPreferencesTest {

    @Test
    @Timeout(60)
    public void routesNotificationsByNamedChannelsAndPersistsAnOptOut() {
        Gson gson = new GsonBuilder().enableComplexMapKeySerialization().create();
        Preferences preferences = gson.fromJson("""
                {
                  "customerId": "customer-73",
                  "enabledChannels": ["email", "text-message"],
                  "destinations": {
                    "email": "reader@example.invalid",
                    "text-message": "+12025550123",
                    "mobile-push": "device-73"
                  }
                }
                """, Preferences.class);

        assertEquals("customer-73", preferences.customerId);
        assertEquals(EnumSet.of(Channel.EMAIL, Channel.SMS), preferences.enabledChannels);
        assertEquals("reader@example.invalid", preferences.destinations.get(Channel.EMAIL));
        assertEquals("+12025550123", preferences.destinations.get(Channel.SMS));
        assertEquals("device-73", preferences.destinations.get(Channel.PUSH));
        assertEquals(List.of("reader@example.invalid", "+12025550123"), preferences.deliveryTargets());

        preferences.enabledChannels.remove(Channel.SMS);
        assertEquals(List.of("reader@example.invalid"), preferences.deliveryTargets());

        String saved = gson.toJson(preferences);
        assertEquals(new JsonParser().parse("""
                {
                  "customerId": "customer-73",
                  "enabledChannels": ["email"],
                  "destinations": {
                    "email": "reader@example.invalid",
                    "text-message": "+12025550123",
                    "mobile-push": "device-73"
                  }
                }
                """), new JsonParser().parse(saved));

        Preferences restored = gson.fromJson(saved, Preferences.class);
        assertEquals(EnumSet.of(Channel.EMAIL), restored.enabledChannels);
        assertFalse(restored.enabledChannels.contains(Channel.SMS));
        assertEquals(preferences.destinations, restored.destinations);
        assertEquals(List.of("reader@example.invalid"), restored.deliveryTargets());
    }

    public enum Channel {
        @SerializedName("email")
        EMAIL,
        @SerializedName("text-message")
        SMS,
        @SerializedName("mobile-push")
        PUSH
    }

    public static class Preferences {
        private String customerId;
        private EnumSet<Channel> enabledChannels;
        private Map<Channel, String> destinations;

        public List<String> deliveryTargets() {
            List<String> targets = new ArrayList<>();
            for (Channel channel : enabledChannels) {
                targets.add(destinations.get(channel));
            }
            return targets;
        }
    }
}
