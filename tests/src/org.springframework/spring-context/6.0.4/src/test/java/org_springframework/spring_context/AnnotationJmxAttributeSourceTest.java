/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_context;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import org.springframework.jmx.export.annotation.AnnotationJmxAttributeSource;
import org.springframework.jmx.export.metadata.ManagedNotification;

public class AnnotationJmxAttributeSourceTest {

    @Test
    void createsManagedNotificationMetadataArrayFromRepeatableAnnotations() {
        final AnnotationJmxAttributeSource source = new AnnotationJmxAttributeSource();

        final ManagedNotification[] notifications = source.getManagedNotifications(
                AnnotatedNotificationsBean.class);

        assertEquals(2, notifications.length);
        assertNotification(
                notifications[0],
                "firstNotification",
                "first description",
                "first.created",
                "first.updated");
        assertNotification(
                notifications[1],
                "secondNotification",
                "second description",
                "second.created");
    }

    private static void assertNotification(
            ManagedNotification notification,
            String name,
            String description,
            String... notificationTypes) {

        assertEquals(name, notification.getName());
        assertEquals(description, notification.getDescription());
        assertArrayEquals(notificationTypes, notification.getNotificationTypes());
    }

    @org.springframework.jmx.export.annotation.ManagedNotification(
            name = "firstNotification",
            description = "first description",
            notificationTypes = {"first.created", "first.updated"})
    @org.springframework.jmx.export.annotation.ManagedNotification(
            name = "secondNotification",
            description = "second description",
            notificationTypes = "second.created")
    public static final class AnnotatedNotificationsBean {
    }
}
