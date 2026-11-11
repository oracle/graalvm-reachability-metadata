/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_context;

import org.junit.jupiter.api.Test;
import org.springframework.jmx.export.annotation.AnnotationJmxAttributeSource;
import org.springframework.jmx.export.annotation.ManagedNotification;
import org.springframework.jmx.export.annotation.ManagedResource;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AnnotationJmxAttributeSourceTest {
    @Test
    void convertsRepeatableNotificationAnnotationsToMetadataArray() {
        AnnotationJmxAttributeSource source = new AnnotationJmxAttributeSource();
        var notifications = source.getManagedNotifications(QueueMonitor.class);
        assertEquals(2, notifications.length);
        assertEquals("queue.ready", notifications[0].getNotificationTypes()[0]);
        assertEquals("Ready", notifications[0].getName());
        assertEquals("queue.empty", notifications[1].getNotificationTypes()[0]);
        assertEquals("Empty", notifications[1].getName());
    }

    @ManagedResource
    @ManagedNotification(name = "Ready", notificationTypes = "queue.ready")
    @ManagedNotification(name = "Empty", notificationTypes = "queue.empty")
    public static class QueueMonitor {
    }
}
