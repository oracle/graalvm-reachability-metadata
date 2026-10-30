/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_ojdbc.ons;

import oracle.ons.Notification;
import oracle.ons.NotificationProperty;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NotificationTest {
    @Test
    void createsEventWithPayloadAndDeliveryTargets() {
        byte[] payload = "service=orders status=up".getBytes(StandardCharsets.UTF_8);
        Notification notification = new Notification("database/event/service", "orders", "node-a", payload);

        assertEquals("event", notification.getVerb());
        assertEquals("database/event/service", notification.getEventType());
        assertEquals(notification.getEventType(), notification.type());
        assertEquals("orders", notification.affectedComponents());
        assertEquals("node-a", notification.affectedNodes());
        assertArrayEquals(payload, notification.getBody());
        assertArrayEquals(payload, notification.body());
        assertFalse(notification.isSystemNotification());
        assertNull(notification.getException());
        assertFalse(notification.getLocalOnly());
        notification.setLocalOnly();
        assertTrue(notification.getLocalOnly());
    }

    @Test
    void storesUpdatesAndEnumeratesApplicationProperties() {
        Notification notification = new Notification("event");
        assertSame(notification, notification.put("service", "orders"));
        notification.setProperty("status", "starting");
        notification.setProperty("status", "up");

        assertTrue(notification.hasProperty("service"));
        assertEquals("orders", notification.get("service"));
        assertEquals("up", notification.getProperty("status"));
        assertEquals("up", notification.getNonNull("status", "unknown"));
        assertFalse(notification.hasProperty("missing"));
        assertNull(notification.getProperty("missing"));
        assertEquals("unknown", notification.getNonNull("missing", "unknown"));

        Map<String, String> properties = new HashMap<>();
        for (NotificationProperty property : notification.getAllProperties()) {
            properties.put(property.name(), property.value());
        }
        assertEquals(Map.of("service", "orders", "status", "up"), properties);
        assertTrue(notification.toNiceString().contains("service:orders"));
        assertTrue(notification.toNiceString().contains("status:up"));
    }

    @Test
    void exposesRoutingAndOriginProperties() {
        Notification notification = new Notification("database/event/service", "orders", "node-a", null);
        notification.put("generatingComponent", "database")
                .put("generatingProcess", "worker-1")
                .put("generatingNode", "node-b")
                .put("clusterId", "cluster-1")
                .put("clusterName", "production")
                .put("instanceId", "instance-1")
                .put("instanceName", "orders-primary");

        assertEquals("database", notification.generatingComponent());
        assertEquals("worker-1", notification.generatingProcess());
        assertEquals("node-b", notification.generatingNode());
        assertEquals("cluster-1", notification.clusterId());
        assertEquals("production", notification.clusterName());
        assertEquals("instance-1", notification.instanceId());
        assertEquals("orders-primary", notification.instanceName());
        assertEquals("database/event/service", notification.get("EVENTTYPE"));
        assertTrue(notification.hasProperty("EVENTTYPE"));
        assertArrayEquals(new byte[0], notification.getBody());
    }

    @Test
    void exposesResultMessageAndSubscriberList() {
        Notification notification = new Notification("response");
        assertEquals("no message", notification.getMessage("no message"));
        notification.put("Result", "success")
                .put("Message", "registered")
                .put("SubscriberID", "subscriber-a; subscriber-b");

        assertEquals(Notification.RESULT_SUCCESS, notification.getResult());
        assertEquals("registered", notification.getMessage("no message"));
        assertArrayEquals(new String[]{"subscriber-a", "subscriber-b"}, notification.getSubscribers());
    }
}
