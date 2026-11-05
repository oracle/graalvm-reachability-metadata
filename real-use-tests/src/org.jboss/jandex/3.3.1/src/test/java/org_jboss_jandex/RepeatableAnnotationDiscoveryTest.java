/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_jandex;

import java.io.IOException;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Jandex's repeatable annotation subsystem to build an application event subscription registry.
public class RepeatableAnnotationDiscoveryTest {
    @Test
    @Timeout(60)
    public void discoversAllEventSubscriptionsFromARepeatableAnnotation() throws IOException {
        Index index = Index.of(EventConsumer.class, Subscription.class, Subscriptions.class);
        DotName subscriptionName = DotName.createSimple(Subscription.class);
        ClassInfo consumer = index.getClassByName(DotName.createSimple(EventConsumer.class));

        List<AnnotationInstance> subscriptions = index.getAnnotationsWithRepeatable(subscriptionName, index).stream()
                .filter(annotation -> annotation.target().asClass().name().equals(consumer.name()))
                .toList();

        assertEquals(List.of("orders.cancelled", "orders.created"), subscriptions.stream()
                .map(annotation -> annotation.value("topic").asString())
                .sorted()
                .toList());
        assertEquals(List.of("orders.cancelled", "orders.created"), consumer
                .annotationsWithRepeatable(subscriptionName, index).stream()
                .map(annotation -> annotation.value("topic").asString())
                .sorted()
                .toList());
    }

    @Subscription(topic = "orders.created")
    @Subscription(topic = "orders.cancelled")
    private static final class EventConsumer {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Repeatable(Subscriptions.class)
    private @interface Subscription {
        String topic();
    }

    @Retention(RetentionPolicy.RUNTIME)
    private @interface Subscriptions {
        Subscription[] value();
    }
}
